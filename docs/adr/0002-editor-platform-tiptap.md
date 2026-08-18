# ADR 0002 — Tiptap/ProseMirror editor platform

**Status:** accepted for the local/private MVP on 2026-08-15  
**Owns:** `P0-009`; Phase 2 owns server validation, migrations, and publish/draft rules.

## Context

Odoc needs an extensible, keyboard-first document editor and a deterministic read-only
renderer. The editor must remain usable without proprietary extensions under the
project's Apache-2.0 license and must not silently make an editor library's internal
format the persistence contract.

We compared the viable open-source directions for the MVP:

| Option | Decision evidence | Result |
| --- | --- | --- |
| Tiptap 3 / ProseMirror | Current retained prototype supports headings, lists, task lists, tables, links, undo/redo, media nodes, paste/drop, keyboard behavior, and a read-only renderer. The core packages used are MIT-licensed and pinned in `odoc-react/pnpm-lock.yaml`. | Selected. |
| Direct ProseMirror | Maximum low-level control, but requires Odoc to own substantially more command, React lifecycle, extension, and toolbar plumbing before product work can progress. | Not selected for MVP; Tiptap remains a ProseMirror-compatible escape hatch. |
| Lexical | A retained test-only `@lexical/headless` 0.49.0 probe constructs headings, lists, and hostile text with the supported node registry. It is MIT-licensed and confirms a viable alternate core, but has no drop-in compatibility with Odoc's current Tiptap media node, toolbar commands, or persisted envelope. | Deferred; reconsider only if the selected stack cannot meet performance, accessibility, or collaboration requirements. |

## Decision

Use pinned OSS Tiptap 3 extensions over ProseMirror. Odoc owns the logical document
format: a versioned JSON envelope with an allowlisted node, mark, and media-attribute
registry in `odoc-react/src/app/documentModel.ts`. The editor and viewer both parse this
same format. Unknown content becomes inert fallback text; unsupported marks and media
attributes are removed rather than rendered as arbitrary HTML or remote behavior.

The current schema version is `1`. It covers document, paragraphs, headings 1–4,
ordered/bullet/task lists, block quotes, code blocks, tables, horizontal rules, safe
inline marks, and Odoc media nodes. A schema change requires fixtures, an explicit
migration, reader compatibility behavior, and a server-side validator in `P2-206`.

## Evidence and constraints

- `documentModel.test.ts` covers legacy Markdown conversion, schema envelope round
  trips, unknown-node fallback, media-attribute allowlisting, local pre-upload
  validation, and a 10,000-block round trip.
- `lexicalComparison.test.ts` is a retained, headless Lexical 0.49.0 comparison
  probe. It proves the common heading/list and hostile-text subset can be represented
  without a DOM, but intentionally does not enter the production bundle or attempt a
  misleading JSON-format conversion.
- The live Playwright suite exercises keyboard editing, intentional blank paragraphs,
  task-list indent/outdent, media insert/position/caption, cancellation, and viewer
  persistence against Compose.
- The lazy editor bundle is currently approximately 149 KiB gzip in a production build.
  This is an observed baseline, not a permanent budget; Phase 6 establishes budgets.
- No Tiptap paid extension is a product dependency. Any paid, cloud, or proprietary
  add-on remains excluded from the Apache-2.0 core unless separately approved and kept
  outside the core distribution.

## Consequences and reversal

The browser still holds authorized plaintext while rendering or editing. This is
compatible with the selected managed-encryption MVP profile but is not an E2EE claim.
The current Postgres string persistence is a temporary MVP adapter; `P2-206` moves
validation/migration ownership server-side, and `P1-115` controls encrypted persistence.

If Odoc later replaces Tiptap, it must preserve the versioned envelope or deliver a
tested one-way migration and a safe fallback viewer for historical documents.
