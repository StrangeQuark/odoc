# Support, deployment, browser, accessibility, and retention policy

**Status:** proposed foundation policy

**Roadmap package:** `P0-001`

**Last reviewed:** 2026-08-14

## Support channels and expectations

Odoc is community-supported unless a separate provider states otherwise. Public bug
reports and feature proposals belong in the relevant GitHub repository. Security
reports follow [SECURITY.md](../../SECURITY.md) and must not be opened publicly.

Before 1.0, releases may make breaking API/schema/configuration changes with migration
notes. Only the latest pre-1.0 minor receives routine fixes. The GA policy must be
ratified by `P6-613`; the initial goal is:

- semantic versioned application releases and separately versioned charts/contracts;
- security and critical data-loss fixes for the latest minor and previous minor for
  at least six months after a superseding minor, when an upgrade path exists;
- documented database N-1 rolling compatibility, not indefinite old-client support;
- advance deprecation in release notes for at least one supported minor unless an
  urgent security removal is necessary;
- no promise for modified builds, unsupported databases/object stores/ingresses, or
  unmaintained browser versions.

Security severity, exploitability, supported versions, and safe-fix availability drive
embargo and patch timing; this document does not promise an unsafe universal deadline.

## Supported deployment classes

| Class | Purpose and support boundary |
|---|---|
| Developer Compose | Reproducible local development/test environment with generated ignored test PKI, per-hop TLS, explicitly non-production credentials, and disposable optional IdP/mail services; never a production security topology |
| Community container deployment | Published frontend/API/worker images with verified TLS on edge, upstream, dependency, management, and telemetry hops; external PostgreSQL, S3-compatible object storage, OIDC, and production KMS/secret provider; best-effort community support |
| Kubernetes/Helm production class | Primary 1.0 topology: external HA PostgreSQL/object storage/identity/KMS, separate API/worker/parser workloads, migration Job, verified per-hop TLS/mTLS, policy-enforcing networking, telemetry, backup, and autoscaling within a connection budget |
| Source build | Supported only when the documented pinned wrappers/toolchains and unmodified gates pass; global developer tooling is not authoritative |

The production Helm chart does not bundle PostgreSQL, MinIO, or a development identity
provider. Single-node deployments have a lower availability class and must not claim
HA. Multi-region active/active is not initially supported.

Exact versions for Java, Node, browsers, PostgreSQL, Kubernetes, and dependencies are
selected and pinned in `P0-002`–`P0-004`; this policy intentionally does not freeze
versions before that verification.

## Browser and device policy

At each release candidate, test:

- the latest two stable major versions available in CI for Chromium/Chrome and Edge;
- the latest two stable Firefox versions and current Firefox ESR;
- the latest two generally available Safari/WebKit major versions on supported Apple OSes;
- current Chrome on Android and Safari on iOS for responsive reading and critical
  authoring smoke, while native mobile applications remain a non-goal.

Playwright Chromium/Firefox/WebKit provides continuous coverage, but real-browser and
assistive-technology checks remain required for critical flows. Internet Explorer,
obsolete embedded WebViews, beta/nightly browser builds, and browsers without required
secure platform APIs are unsupported. A browser may be removed only with release-note
notice, usage/standards/security rationale, and a migration path where feasible.

## Accessibility policy

Odoc targets [WCAG 2.2](https://www.w3.org/TR/WCAG22/) Level AA for authenticated,
anonymous, authoring, reading, administration, and error/recovery flows.

The release baseline includes keyboard-only operation, visible/unobscured focus,
semantic landmarks/headings, accessible names/roles/states, non-color communication,
text alternatives, drag alternatives, 320 CSS-pixel reflow, 200–400% zoom/text spacing,
forced colors/high contrast, reduced motion, touch alternatives, and understandable
authentication. Automated axe checks supplement rather than replace manual NVDA +
Firefox and VoiceOver + Safari testing. Known limitations are public, severity-ranked,
owned, and dated; Odoc does not call automation alone accessibility certification.

## Initial retention assumptions

Operators can configure retention within safe minimum/maximum bounds. Legal holds and
regulatory requirements remain deployment responsibilities until explicitly implemented;
until Odoc has a tested hold workflow, an operator must suspend affected purge jobs by
policy and Odoc must not claim that its records are hold-aware.
The following are starting defaults to validate in ADRs and feature packages:

| Data | Initial assumption |
|---|---|
| Published page versions | Retain until explicit authorized lifecycle/export/delete policy; never silently age out authored history |
| Drafts | Retain while active; abandon cleanup no sooner than 30 days after last edit with recovery warning/policy |
| Trash | 30 days before purge eligibility; purge remains explicit, audited, and idempotent; operator-applied holds block it externally until Odoc implements the selected hold workflow |
| Upload sessions/orphan staging | Expire sessions after 24 hours; reconcile only validated unreferenced objects |
| Invitations/passwordless state | Seven days or shorter deployment policy; hashed, single-use, and deleted after bounded diagnostic retention |
| Sessions | Identity/session ADR policy; server revocation state retained only as long as needed to enforce expiry and audit |
| Audit events | 365 days by default with configurable export/retention and purpose-specific encryption; no content bodies |
| Notification records | Read notifications 90 days, unread notifications up to 365 days; content disclosure remains minimized |
| Job attempts/dead letters | Successful diagnostics 30 days, failed/dead-letter diagnostics 90 days; never retain raw sensitive payload in diagnostics |
| GitHub webhook metadata | 30 days; raw payload off by default or shortest threat-model-approved troubleshooting window |
| Repository snapshots | Active and last-good protected; superseded snapshots 30 days by default subject to pin/export/retention policy |
| Public-share verification state | Until revoke/expiry plus bounded non-secret audit record; raw token is never retained |
| Product telemetry | Off by default; when enabled use the shortest documented aggregate retention, initially at most 30 days raw/13 months aggregate |
| Backups/PITR | Set from approved RPO/RTO and deployment policy; deletion lag and encryption-key retention are explicit and restore-tested |

Deleting a live record does not imply instant removal from immutable protected backups.
The UI and privacy documentation must state the backup expiry window. Encryption key
deletion/crypto-shredding is delayed until retention, legal hold, backup, and restore
impact are proven.

## Data portability and end of support

Production 1.0 requires a documented, versioned native export and restore path that does
not contain provider credentials. Before ending support for a format/version, Odoc must
offer and test an upgrade or export path. Derived search and repository projections may
be rebuilt; canonical authored content, attachments, identity mapping, and encryption
keys require explicit protection and recovery instructions.

## Policy review

Review this file at every milestone, when a supported platform reaches end of life, after
a material security/accessibility/data-loss incident, and before changing default
retention. Release notes identify support-policy changes.
