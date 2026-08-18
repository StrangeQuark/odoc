# Odoc capability and release matrix

**Status:** accepted MVP-development scope; public-release operations remain separately
gated.

**Roadmap package:** `P0-001`

**Last reviewed:** 2026-08-14

This document turns “an open-source Confluence alternative” into bounded product
claims. A capability is not shipped merely because a screen or endpoint exists.
Its backend authorization, durable data model, accessible UI, tests, migrations,
telemetry, recovery behavior, and documentation must pass the named release gate.

## Standard terminology

| Term | Product meaning |
|---|---|
| Instance | One deployed Odoc installation and its operational administration boundary |
| Workspace | Tenant and security boundary containing members, groups, settings, and one immutable encryption profile |
| Space | Documentation collection inside a workspace, with a home page and optional narrower permissions |
| Page | Stable content identity in a tree; titles and slugs are mutable presentation data |
| Page version | Immutable published snapshot of versioned canonical editor content and metadata |
| Draft | Mutable, revision-controlled authoring state that is separate from the published version |
| Attachment | Authorized metadata and lifecycle record for encrypted object-storage bytes |
| Repository binding | Association between a workspace/space and an authorized GitHub repository plus branch policy |
| Repository snapshot | Immutable, commit-consistent repository metadata, README, and generated API documentation |
| Public share | Revocable and scoped anonymous capability, never equivalent to membership |
| Macro or block | Versioned typed editor node with safe editor, viewer, export, search, and fallback behavior |
| Managed encryption | Application-layer encryption at rest whose keys can be requested by authorized Odoc services; not E2EE |
| Zero-knowledge workspace | Separately gated profile in which enrolled honest clients hold content keys and the reviewed protocol/server-held state does not let Odoc servers decrypt content; an operator that can serve malicious client code can still target future keys/plaintext, so this web-client limitation is disclosed and independently assessed |

## Release vocabulary and exit criteria

| Stage | Meaning | Required exit evidence |
|---|---|---|
| Preview / M0 | Installable engineering foundation, not a documentation product | `P0-001`–`P0-016` followed by formal `M0-GATE`; clean backend/frontend gates; Compose thin slice; `kind` chart smoke; versioned contracts; foundational ADRs; non-root images |
| Documentation MVP / M1 | A small organization can safely author and find private documentation | `P1-100`–`P1-113`, `P1-115`, and `P1-116` (`P1-114` is a later sandbox enabler); `P2-200`–`P2-231`; `P3-300`–`P3-303`; tenant/IDOR, editor schema/XSS, managed-encryption raw-store/key-recovery, migration, accessibility, live E2E, and pod-replacement evidence |
| Collaboration beta / M2 | Teams can discuss, discover, watch, notify, and share scoped content publicly | `P3-304`–`P3-316`; current authorization at query/read time; idempotent notifications; public-share threat model and browser/security evidence |
| GitHub documentation beta / M3 | GitHub binding, safe README, and static Java/JavaDoc browsing are usable | `P4-400`–`P4-420` plus `P4-423`; commit-consistent snapshots; mock GitHub pipeline; parser isolation/no-code-execution evidence |
| Production 1.0 / M4 | Supported self-hosted release with production operations | A release manifest frozen before release-candidate testing; at minimum `P5-500`, `P5-503`, `P5-506`–`P5-508`, `P5-510`, `P5-513`, `P5-516`, `P5-520`, and `P5-528`, plus its exact additional selected leaves, followed by `P5-527`; all `P6-600`–`P6-615`; the exact applicable `QA-*` packages named in that manifest; install/upgrade/restore/rollback evidence; approved SLOs; no unresolved release-blocking security issue |
| Parity releases / M5 | Named Confluence-parity tracks mature incrementally | Exact selected Phase 5 leaves plus `P5-527`; each public claim has contract, UI, QA, runbook, import/export or fallback, and documented differences |

“Beta” means the capability is usable but may have documented compatibility or
operational limits. “Experimental” means it is off by default, has no compatibility
promise, and cannot be required for data recovery. “Deferred” is not shipped.

Stages inherit their preceding gates: MVP means M1, collaboration beta means M2,
GitHub beta means M3, 1.0 means M4, and parity means M5. M0 is not complete until
`M0-GATE` passes. Both M4 and M5 claims require `P5-527`. Before an M4 or M5
candidate is built, its release manifest must replace “selected” and “applicable” with
exact package and QA IDs; an unresolved manifest is not an exit gate.

“Public beta” in an individual capability row means that capability ships through a
documented pre-1.0 beta channel after its own package/QA gate and before the M4 GA
candidate. It does not silently add the capability to collaboration M2 or GitHub M3.

### Pending scope decisions mirrored in the current roadmap

The current roadmap §2.4 and §20 already mirror the pending proposals below so its
package graph and this policy do not contradict each other while `P0-001` is reviewed.
Copying them into the roadmap does not approve them: they take effect only with explicit
project-owner approval, and a rejected choice requires both documents and the ledger to
be amended together before implementation:

- basic comments and notifications are scheduled for M2 rather than M1 so M1 retains the exact package
  set in roadmap §20; M1 still includes watches as durable content metadata;
- zero-knowledge/E2EE receives an architecture and compatibility decision at M0 but
  no M1/M2 product-preview promise; an optional restricted profile can ship no earlier
  than M4 after its independent cryptographic gate;
- managed application-layer encryption is the mandatory default profile for every
  classified first write and permits authorized services to decrypt transiently; it is
  never called E2EE. The optional zero-knowledge profile is a separate reduced-feature
  product gated by `P5-533`, `P5-534`, any selected `P5-535` leaves, `P5-536`, the
  exact release-scoped `QA-019-ZK-<release>-<manifest-generation>` evidence child,
  interoperability vectors,
  and independent review;
- scoped public sharing is selected for M2 through `P3-313`–`P3-316`;
- selected analytics/tasks/status basics are scheduled no earlier than a
  pre-M4 feature beta; the optional beta read API is not selected;
- blog/news/announcements are deferred from pre-M4 releases to the M5 parity track
  through `P5-529` and `P5-527`; generic saved views,
  template variables, comment moderation, additional notification channels, external
  search providers, additional API-documentation languages, broader attachment preview
  providers, and multi-region guidance remain explicitly deferred until a newly assigned
  implementation package exists and the later claim passes `P5-527`;
- built-in email/password accounts with verified-email enrollment are the MVP identity
  boundary. The approved local/private profile permits normal self-service registration;
  OIDC/OAuth/SSO are optional provider-linking features, while an invite-only/allowlist
  enrollment policy is a future explicit deployment choice. SAML broker, SCIM, and directory lifecycle are
  deferred to the M5 enterprise track through `P5-530`;
- native portable restore, Markdown/HTML interchange, and the Confluence importer are
  public-beta capabilities delivered by `P5-506`–`P5-508` before the M4 candidate;
  native restore remains mandatory for 1.0.
- browser print remains an M1 capability in `P2-215`; the asynchronous server PDF
  export service `P5-509` is deferred to M5.

These are scope decisions, not claims that missing code exists. If the owner rejects
one, the roadmap, this matrix, milestone package sets, and implementation ledger must be
amended before `P0-001` is approved.

## Capability commitments and traceability

| Capability family | Earliest commitment | Owning packages and required gate |
|---|---|---|
| Workspaces, members, groups, and roles | MVP | `P1-100`–`P1-106`, `P1-113` |
| Authentication and sessions | MVP, self-service local email/password with verified-email enrollment and secure sessions | `P1-101`, `P1-102`, `P1-108`, `P1-113`; optional OIDC/OAuth linking is in the same identity model, an invite-only/allowlist policy requires an explicit deployment decision, and MFA/passkeys remain `P5-531` hardening work |
| Enterprise identity and directory provisioning | M5 enterprise track; explicitly deferred from M4 | `P5-530`; SAML/SCIM claims require its contract, migration, security, and QA evidence |
| Central authorization and tenant isolation | MVP | `P1-104`, `P1-113`, `QA-003`, `QA-008` |
| Audit and administrative visibility | MVP baseline; beta UI | `P1-107`, `P3-312`, `P5-520`, `QA-003` |
| Spaces and page trees | MVP | `P2-200`–`P2-205`, `P2-229`–`P2-231`; bulk administration is `P5-513` at M4 |
| Move, copy, archive, trash, restore, and purge | MVP | `P2-202`–`P2-205`, `P2-220`, `P2-229`–`P2-231`; bulk administration is `P5-513` at M4 |
| Rich editor and deterministic read-only viewer | MVP core blocks | `P0-009`, `P2-206`–`P2-215`, `QA-006`; advanced macros use `P5-503`–`P5-505` |
| Images and attachments | MVP; safe common previews/text extraction at public beta | `P1-110`, `P2-216`–`P2-218`, `P2-231`; scanning/extraction/OCR is `P5-528`; broader preview providers require a newly assigned implementation package and then `P5-527` |
| Drafts, autosave, crash recovery, conflicts | MVP | `P2-209`, `P2-212`–`P2-214`, `P2-231` |
| Immutable history, diff, restore | MVP | `P2-209`, `P2-219`, `P2-220`, `P2-231` |
| Labels, favorites, watches, recent content | MVP | `P2-221`, `P2-222`, `P2-231`; automation is `P5-518`; generic saved views require a newly assigned implementation package and then `P5-527` |
| Templates | MVP starter set; workspace/space templates at collaboration beta | `P2-223`, `P2-224`, `P2-231`; variables/workflow templates require a newly assigned implementation package and then `P5-527` |
| Internal links, headings, backlinks | MVP | `P2-225`, `P2-226`, `P2-231` |
| Fine-grained page/space restrictions and guests | Core RBAC/restrictions at MVP; guests at collaboration beta | `P1-103`–`P1-106`, `P2-227`, `P2-228`, `P2-231`, `QA-008`; public shares are separately gated below |
| Full-text page/space search | MVP; M2 hardening and repository/API/discussion sources later | `P3-300`–`P3-303` plus the exact M1 `QA-009` child; `P3-316` is the M2 integrated hardening gate, repository/API search is `P4-419`, and discussion search is `P5-532`; an external provider requires a newly assigned package behind `P3-300` and then `P5-527` |
| Discovery and command palette | Beta | `P3-304`, `P3-316` |
| Page and inline comments | Collaboration beta / M2 under the pending foundation scope | `P3-305`–`P3-308`, `P3-316`; moderation/workflow beyond thread lifecycle requires a newly assigned implementation package and then `P5-527` |
| Mentions, reactions, and task primitives | Beta | `P3-309`–`P3-311`, `P3-316`; durable personal task views use `P5-511` |
| Watches, notifications, and email preferences | Collaboration beta / M2 under the pending foundation scope | `P3-310`, `P3-311`, `P3-316`; scheduled digests and channels beyond in-app/email require a newly assigned implementation package and then `P5-527` |
| Public shares, anonymous reading, and SEO | Collaboration beta / M2 (selected) | `P0-002` SEO decision, `P3-313`, `P3-314`, `P3-316`, `QA-017`; branding/custom-domain work is `P5-515` and requires its own domain/TLS ADR |
| Activity stream | Beta | `P3-315`, `P3-316` |
| GitHub App tethering and repository overview/URL | GitHub beta | `P4-400`–`P4-409`, `P4-423` |
| Safe commit-pinned README display | GitHub beta | `P4-410`, `P4-411`, `P4-423`, `QA-010` |
| Static Java/JavaDoc extraction and browser | GitHub beta | `P1-114`, `P4-412`–`P4-418`, `P4-423`, `QA-010`; additional programming languages require a newly assigned implementation package and then `P5-527` |
| Repository/README/Java symbol search | GitHub beta | `P4-419`, `P4-423`, `QA-009` |
| GitHub Enterprise and other providers | Parity track | `P4-421`, `P4-422`; not part of GitHub.com beta |
| Portable native archive/export and restore | Public beta and required for 1.0 | `P5-506`, `P6-608`, `QA-016` |
| Markdown/HTML and Confluence import | Public beta before the M4 candidate | `P5-507`, `P5-508`, `QA-016`; compatibility remains construct/version specific |
| Browser print and PDF export | Browser print at MVP; server-generated PDF deferred to M5 | Browser print: `P2-215` and `QA-006`; server PDF: `P5-509` and `QA-011`, then `P5-527` |
| Content status, review, approval, scheduling | Required for 1.0 | `P5-510`; traced at `P5-527` |
| Administration, retention, privacy, lifecycle | Required for 1.0 | `P5-513`, `P5-520`, `P6-608`, `P6-614` |
| Real-time co-editing and presence | Decision/prototype first | `P5-500`; implementation only through `P5-501`, `P5-502` after approval |
| Versioned macro registry | Pre-M4 enabling infrastructure for import/fallback safety | `P5-503`; this does not claim that any optional macro family ships |
| Documentation macros, diagrams, equations, embeds | M5 parity track | `P5-504`, `P5-505`; each family separately gated |
| Analytics, content health, tasks, and status | Selected basics no earlier than M4; parity thereafter, privacy approval required | `P5-510`–`P5-512` |
| Automation rules | Parity track | `P5-518`; arbitrary scripts remain prohibited |
| REST API, service accounts, outbound webhooks | M5 developer-platform track; no separate beta read API selected | `P5-519` |
| Safe plugin/extension SDK | Decision/prototype first | `P5-521`; no arbitrary same-origin JavaScript or server classpath loading |
| Structured databases/tables | Separate parity product | `P5-522`; implementation packages require a new approved ADR |
| Whiteboard/canvas | Separate parity product | `P5-523`; implementation packages require an approved accessibility/export prototype |
| Calendar/timeline/roadmap | Separate parity product | `P5-524`; calendar and timeline require separate leaves |
| Blog/news/announcements | M5 parity track; explicitly deferred from pre-M4 releases | `P5-529`, then `P5-527` claim gate |
| Internationalization and RTL | Architecture-ready from Preview; catalog extraction, pseudo-locale qualification, and RTL readiness before M4; real translated catalogs require separately owned translation/review leaves | `P0-002`, `P5-516`, `P6-612` |
| Managed encryption at rest and TLS | Architecture at M0; mandatory before Phase 1 sensitive persistence and throughout M1 onward | `P0-016`, `P1-115`, `P1-116`, exact release-scoped `QA-019-MANAGED-<release>` evidence; production KMS/rotation/backup/network hardening use `P6-605`, `P6-606`, and `P6-608` at M4 |
| Zero-knowledge E2EE workspaces | Architecture/compatibility at M0; optional restricted profile no earlier than M4 | `P0-016`; implementation requires `P5-533`, `P5-534`, and exact selected `P5-535` leaves; claim gate is `P5-536` plus the exact release-scoped `QA-019-ZK-<release>-<manifest-generation>` evidence child and independent assessment; no E2EE claim before all gates pass |
| Docker, Compose, Kubernetes, and autoscaling | Preview baseline; hardened for 1.0 | `P0-010`–`P0-015`, `P6-600`–`P6-615`, `QA-014`, `QA-015` |
| Backups, disaster recovery, upgrades, releases | Required for 1.0 | `P6-602`, `P6-608`, `P6-613`–`P6-615`, `QA-016`–`QA-018` |
| Profiles, preferences, and active-session management | M4 selection/parity | `P5-514` |
| Keyboard shortcuts and help | M5 usability track | `P5-517` |
| Smart links and additional providers | M5, one threat-modeled provider leaf at a time | `P5-505`, `P5-525`, `P5-527` |
| Native mobile and full offline-first use | Explicit non-goal through M5; evaluation only after measured need | `P5-526`; no production client exists without new implementation leaves |
| Multi-region active/active | Explicitly unsupported through M4; guidance/decision only if demanded | `P6-614` documents the boundary; implementation requires a newly assigned package and then `P5-527` |

The selected baseline is mandatory managed application-layer encryption plus a later,
optional zero-knowledge profile. Managed workspaces must never be advertised as E2EE,
because authorized Odoc services can request keys and process plaintext transiently.

## Explicit first-release non-goals

- A microservice per domain module or an in-chart production database/object store.
- Executing Maven, Gradle, compilers, annotation processors, repository classes, or
  any repository-controlled program to produce documentation.
- Arbitrary JavaScript plugins, same-origin page scripts, or server classpath plugins.
- Native mobile applications, full offline-first editing, or unapproved CRDT work.
- OpenSearch/Elasticsearch before measured PostgreSQL search limits require it.
- MFA/passkeys or a weaker password/recovery flow that bypasses the `P1-101` identity contract before `P5-531` is approved.
- Claiming every Confluence macro, import shape, or enterprise feature is compatible.
- Calling managed server-side encryption “end-to-end encryption.”

## Claim and change control

1. Product pages and release notes use only the stage and limitations in this file.
2. A package identifier is necessary traceability, not proof; its acceptance evidence
   must be linked from the implementation ledger and release manifest.
3. A deferred or experimental capability cannot be described as available by default.
4. Compatibility with Confluence is stated per fixture/version/construct, never broadly.
5. The project owner approves changes to release commitments, licensing, public
   security/cryptographic claims, and removal of a portability promise.
6. `P5-527` re-audits this matrix before the M4 baseline or any later parity claim;
   `P6-615` re-audits it before GA.
