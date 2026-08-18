# Representative Odoc user journeys

**Status:** proposed foundation policy; project-owner approval is required before
`P0-001` is complete.

**Roadmap package:** `P0-001`

**Last reviewed:** 2026-08-14

These journeys define outcomes rather than screen designs. Each step must remain
authorized, accessible, recoverable, and diagnosable across retries and pod changes.

## Workspace owner establishes a private knowledge base

1. Accept an invite, verify email, and sign in with an email/password account; optionally use a configured, linked OIDC provider.
2. Create a managed-encryption workspace after reviewing recovery consequences and
   become its first owner atomically. A zero-knowledge choice is not displayed until
   the separate E2EE implementation and claim gates pass.
3. Invite contributors and readers, organize groups, and verify effective roles.
4. Create a team space and a restricted child space/page area.
5. Inspect audit events without seeing page bodies, credentials, or excessive PII.
6. Configure retention, export, backups, and operational integrations before launch.

**Success evidence:** `P0-016`, `P1-101`–`P1-116`, `P2-200`, `P2-227`, portable
export/restore `P5-506`, administration/retention `P5-520`, backup/restore `P6-608`,
and tenant/IDOR and managed-encryption suites pass with two workspaces and two API
replicas. Selecting zero knowledge additionally requires `P5-533`, `P5-534`, the
exact selected `P5-535` leaves, `P5-536`, and the immutable release-scoped
`QA-019-ZK-<release>-<manifest-generation>` evidence child; parent or managed-profile
evidence cannot satisfy that claim.

## Contributor authors and recovers polished documentation

1. Navigate an accessible page tree and create a child page from blank or template.
2. Author headings, lists, tables, code, callouts, links, and images with keyboard or pointer.
3. Observe accurate autosave state, recover from an offline/browser crash, and resolve
   a deliberate concurrent-edit conflict without either version being silently lost.
4. Publish an immutable version with a change message and inspect the safe viewer.
5. Compare history, restore an earlier version as a new version, and print/export it.

**Success evidence:** `P2-202`–`P2-224` including browser print in `P2-215`, portable
archive `P5-506`, and mandatory M4 Markdown/HTML interchange `P5-507`; editor
golden/migration/XSS suites, two-browser conflict E2E, attachment authorization, and
manual editor accessibility notes. Server-generated PDF is a separate deferred
`P5-509` capability.

## Reader discovers only authorized knowledge

1. Search from the header and full search page with shareable filters.
2. Receive relevant safe snippets only from resources currently authorized for them.
3. Follow headings, backlinks, labels, recent/starred pages, and a canonical stable URL.
4. Lose access in another session and see cached/search/media data disappear immediately
   without learning whether a newly private item still exists.

**Success evidence:** `P2-221`–`P2-228`, `P3-300`–`P3-304`, `QA-008`, and
`QA-009`, including cross-user and post-revocation cursor tests.

## Team discusses and follows changes

1. Add a page comment and a keyboard-created inline comment.
2. Mention an authorized teammate, react, resolve the thread, and watch the page.
3. Receive one in-app/email notification despite event replay.
4. Open the notification after permission removal and receive a safe generic denial.

**Success evidence:** `P3-305`–`P3-311`, anchor-edit fixtures, two-user E2E,
notification deduplication, and permission-revocation tests.

## Guest reads a deliberately narrow area

1. Accept a scoped, expiring invitation through a safe relative return path.
2. Navigate only permitted spaces/pages and download only authorized attachments.
3. Comment only if explicitly granted and never see members, history, repository source,
   ancestors, or metadata outside guest policy.
4. Lose access immediately when removed, including cached and signed-URL paths.

**Success evidence:** `P1-102`–`P1-106`, `P2-216`, `P2-227`, guest-comment behavior in
`P3-305`–`P3-308`, the integrated collaboration/security gate `P3-316`, and `QA-008`.

## Anonymous viewer uses a public share

1. Open a high-entropy scoped share in the separate anonymous shell.
2. Read only the selected page/subtree and explicitly allowed media.
3. Never receive authenticated navigation, drafts, comments, history, backlinks,
   private repository assets, author PII, or unrelated ancestor metadata.
4. See access stop after expiry or revocation, including bounded cache invalidation.

**Success evidence:** `P3-313`, `P3-314`, `P3-316`, anonymous browser security
tests, and the public-sharing threat model required by the selected M2 scope.

## Developer tethers a GitHub repository

1. A workspace administrator starts the GitHub App installation with bound state.
2. Select an authorized repository and branch policy without exposing a GitHub token.
3. View repository identity, canonical URL, exact branch and commit SHA.
4. Read a hostile-fixture-tested README with commit-pinned relative links/assets.
5. Browse packages, types, overloads, and JavaDoc extracted statically without running
   builds, compilers, annotation processors, bytecode, or repository programs.
6. Observe a failed new sync while the last complete snapshot remains available, then retry.

**Success evidence:** `P4-400`–`P4-420`, `P4-423`, webhook/revocation tests,
atomic snapshot tests, and sandbox instrumentation from `QA-010`.

## Instance operator installs and scales Odoc

1. Supply external PostgreSQL, object storage, OIDC, SMTP, GitHub, KMS, and telemetry
   references plus verified CA/certificate trust for every application and management hop.
2. Install signed images/chart without committed secrets or bundled production data services.
3. Run one safe migration job, observe readiness, and scale API and workers independently.
4. Rotate a secret/key, drain/replace a pod, inject a worker failure, and verify recovery.
5. Restore database, object versions, and encryption keys into an isolated environment,
   block external side effects, and measure achieved RPO/RTO.
6. Upgrade and follow the supported rollback or forward-fix path.

**Success evidence:** `P6-600`–`P6-615`, `QA-014`–`QA-019`, signed release
manifest, load/autoscaling evidence, and a real restore drill.

## Zero-knowledge workspace member

1. Create or join through an enrolled device and receive a client-wrapped workspace key.
2. Author and read content whose reviewed honest-client protocol leaves network captures
   and server-held state unable to decrypt it. Understand that an operator able to serve
   malicious web-client code could target future keys/plaintext; the product discloses
   this limitation and evaluates a separately distributed client for stronger resistance.
3. See server-search, GitHub parsing, previews, content-aware email, and other incompatible
   operations disabled rather than silently uploading plaintext.
4. Enroll a second device through an existing device/recovery method, revoke a device,
   rotate epochs, and understand that already downloaded plaintext cannot be revoked.
5. Lose all devices without a recovery key and receive an honest unrecoverable result.

**Success evidence:** `P5-533`, `P5-534`, any exact selected `P5-535` leaves,
`P5-536`, and the immutable release-scoped
`QA-019-ZK-<release>-<manifest-generation>` evidence child, including independent
cryptographic review. Until then Odoc makes no E2EE claim.
