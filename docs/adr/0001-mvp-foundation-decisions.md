# ADR 0001 — MVP foundation decisions

**Status:** accepted for local/private MVP development on 2026-08-15  
**Scope:** `P0-002`; public-release requirements remain separately gated.

This compact ADR collection records the 17 foundation decisions required before feature
work. It freezes the conventions that agents need now; a later ADR may supersede one
only with a migration, compatibility, and rollback plan.

## ADR-001 — Modular runtimes

Use a Spring modular monolith, partitioned by feature package. Build one image with
`api`, `worker`, and future restricted `parser` modes; deploy them separately when the
workload needs it. Public HTTP remains in the API mode.

## ADR-002 — Backend toolchain

Use Java 21, Maven Wrapper, Spring Boot 4.1.0, package root
`com.strangequark.odoc`, Flyway migrations, compiler validation, and Maven tests.
Version upgrades are explicit dependency changes with clean-build evidence.

## ADR-003 — Frontend toolchain

Use Node 24, pnpm 11 through Corepack, React 19, Vite 8, TypeScript 5.9, and the
browser policy declared in the support policy. Commit the lockfile and pin production
dependencies exactly.

## ADR-004 — HTTP contract

Use versioned JSON APIs rooted at `/api/v1`, RFC 9457-style problem responses, opaque
UUID resource IDs, optimistic revisions where mutation conflicts matter, and an owned
OpenAPI contract. The React client consumes generated types once P0-008 lands; current
handwritten MVP calls are transitional and must not become the sole contract source.

## ADR-005 — Identity and sessions

Use built-in email/password accounts for the MVP. The approved local/private profile
allows normal self-service registration and requires email verification before workspace
or document access; invitations govern workspace membership. A future deployment may
select an invite-only or allowlist enrollment policy through an explicit configuration
decision. Passwords use an ADR-selected memory-hard one-way hash; the browser uses
secure HttpOnly cookie sessions and CSRF protection, never stored credentials or tokens.
OIDC/OAuth/SSO are optional, explicit account-linking providers. MFA/passkeys are
P5-531 hardening work.

## ADR-006 — PostgreSQL data model

Use PostgreSQL, Flyway, UUID primary keys, JPA for ordinary aggregates, and carefully
reviewed native SQL for recursive/search/maintenance work. Pages initially use an
adjacency-list parent UUID with deterministic sibling order; a future tree strategy
change requires an online migration and performance evidence.

## ADR-007 — Rich documents

Use the pinned open-source Tiptap/ProseMirror stack. The logical canonical document is
versioned JSON with an allowlisted node/attribute registry shared by editor and viewer.
Server validation, migrations, safe rendering, and draft/publish semantics land in the
Phase 2 packages; legacy Markdown is one-way import compatibility only.

## ADR-008 — Managed-profile search

The production managed-encryption profile indexes approved keyed blind tokens and keeps
the searchable source encrypted. It authorizes before final results and bounds in-memory
decrypt/rerank/snippet work. The present PostgreSQL full-text MVP is development-only
and is not evidence of the encrypted-production design. Zero-knowledge workspaces do
not use server plaintext search without a later reviewed protocol.

## ADR-009 — Media and object storage

Use S3-compatible object storage for production media with metadata and references in
PostgreSQL. The current Postgres-bytea media path is a small-file local-MVP adapter and
must not be extended into large-object production storage. Production media is streamed,
authorized, encrypted through the approved adapter, and lifecycle-managed.

## ADR-010 — Jobs and outbox

Use PostgreSQL transactional outbox records and durable leased jobs. Workers use a
database-clock lease epoch/fencing token and idempotency keys; an expired worker cannot
publish after its replacement completes.

## ADR-011 — GitHub integration

Use a GitHub App for repository access and webhook signatures. Tokens stay server-side,
are installation-generation aware, and are never available to the browser. Repository
data is commit-pinned; sync work is durable and can retain a visibly stale last-good
snapshot.

## ADR-012 — Java documentation parsing

Never execute, compile, load, or initialize repository-controlled code. Fetching,
parsing, and publication are separate stages. Parsing runs in a dedicated restricted
runtime with no GitHub, database, SMTP, or Kubernetes credentials and deny-by-default
egress.

## ADR-013 — Deployment ownership

`odoc/deploy` owns Docker Compose, Helm/Kubernetes manifests, and cross-repository
image/version coordination. Each repository owns its own source, build, test, image,
and README. Isolated CI consumes published contracts rather than relying on a sibling
checkout.

## ADR-014 — Public documentation rendering

The MVP is an authenticated React SPA. Anonymous/crawlable documentation requires a
separate, later renderer/SSR decision with cache invalidation and private-data leakage
tests; it is not inferred from the SPA.

## ADR-015 — Realtime collaboration

Realtime multi-user editing is deferred. The MVP uses server revisions, drafts, and
explicit conflict handling. CRDT/WebSocket collaboration needs its own protocol,
authorization, persistence, and operational ADR before implementation.

## ADR-016 — Internationalization

Extract UI strings and use locale-aware formatting. English is the only initial shipped
catalog; pseudo-locale and RTL readiness precede real translated catalogs and their
translation/review program.

## ADR-017 — Transport security

Production uses verified TLS on browser-edge, proxy-service, dependency, management,
and telemetry hops, with explicit trust bundles, SAN/hostname verification, certificate
rotation, and operator-owned PKI. Local Compose may use a documented development
exception; it is never production evidence.

## Consequences and next step

`P0-003` and `P0-004` are already represented by the runnable Spring/React MVP, but
their package acceptance must be reconciled to this ADR and recorded with reproducible
build evidence. The next feature implementation begins only after `M0-GATE`: first
`P1-100`, then the shared managed-encryption core `P1-115`, followed by the built-in
email/password account and secure-session work in `P1-101`. Shared Basic Auth remains
development-only and must not become the production identity boundary.
