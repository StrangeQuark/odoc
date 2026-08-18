# P0-001 foundation approval record

**Status:** core private/local development decisions approved; public-release decisions
and operational contact setup remain pending

**Roadmap package:** `P0-001`

**Last reviewed:** 2026-08-15

This record makes the owner-controlled parts of the Odoc foundation bundle explicit.
Approval is not inferred from repository ownership, silence, continued implementation,
or approval of only one pull request. The owner-approved local/private MVP decisions are
recorded below. Remaining product-policy choices and public-release operations stay
explicitly tracked rather than being inferred from that limited approval.

This is a project decision record, not legal advice. License, name, safe-harbor,
employment/copyright, privacy, and jurisdiction-specific obligations may require
qualified counsel before distribution or operation.

## Decision checklist

| ID | Proposed decision | Main alternative or consequence | Status |
|---|---|---|---|
| `FND-001` | Use the capability stages and exact gates in the [capability matrix](capability-matrix.md). Current roadmap §2.4 and §20 mirror these pending choices; amend the roadmap, matrix, milestones, and ledger together if the owner replaces one | Do not dispatch work against divergent scope; make no ambiguous “Confluence parity” claim | Pending |
| `FND-002` | License Odoc source, tests, build/deploy definitions, and project documentation as `Apache-2.0` | The project does not require network copyleft; retain notices and the Apache patent grant, and revisit only through a future license-change process | Approved by owner 2026-08-15 |
| `FND-003` | Use DCO 1.1 sign-off, no CLA, no copyright assignment to Odoc, and no dual-license program | A CLA/assignment changes contributor expectations and requires a separately approved rights/governance process | Pending |
| `FND-004` | Confirm `@StrangeQuark` as project owner and sole bootstrap maintainer, and adopt the founder-led governance, one-time foundation exception, external-review transition, recusals, and succession gates | Name additional maintainers/legal home now or propose a different community governance model | Pending |
| `FND-005` | Retain `Odoc` as the project and repository name for MVP development and current product use, while adopting the factual-use and official-channel rules in [TRADEMARKS.md](../../TRADEMARKS.md). The documented OCaml/package/software collision risk is accepted for this phase; Odoc makes no trademark-ownership or clearance claim. Obtain qualified legal review before a public 1.0 launch, trademark filing, domain acquisition, or material commercial expansion | Rename before public launch if the qualified review identifies an unacceptable conflict; do not erase or minimize the known collision evidence | Approved by owner 2026-08-15; public-launch review remains required |
| `FND-006` | Require built-in invite-only email/password accounts for MVP, with verified email, secure sessions, recovery, throttling, and audit. OIDC/OAuth/SSO remain optional additional sign-in methods; MFA/passkeys move to the separately gated `P5-531` hardening package | Accept the credential-security scope now, rather than making an external IdP a self-hosting prerequisite | Approved by owner 2026-08-15 |
| `FND-007` | Target the deployment, browser/device, WCAG 2.2 AA, version-support, deprecation, and accessibility/manual-test commitments in the [support policy](../project/support-policy.md) | Revise supported environments and release commitments before bootstrapping toolchains | Pending |
| `FND-008` | Adopt the initial retention, backup disclosure, portability, deletion, and operator legal-hold boundaries in the support policy | Provide replacement periods/workflows and reconcile key deletion/backups before schemas are built | Pending |
| `FND-009` | Treat product telemetry as off by default; prohibit content/query/private identifiers in product metrics and require explicit privacy review for analytics | Define a different lawful, documented data-collection policy before implementation | Pending |
| `FND-010` | Use the provisional availability, latency, scale, queue, repository, RPO/RTO, KMS, E2EE, and browser budgets in [success metrics and risks](success-metrics-and-risks.md) as falsifiable architecture/qualification targets | Replace individual numbers now; later benchmarks may revise them only through recorded evidence and approval | Pending |
| `FND-011` | Choose managed application-layer encryption as the mandatory default profile before every sensitive first write, with authenticated TLS for every network hop. Authorized Odoc services can request keys and decrypt content transiently; this is encrypted storage, not E2EE | Mandatory zero knowledge requires redesigning or disabling server search, GitHub/repository ingestion, previews/OCR, exports/imports, notification content, analytics, and other server-plaintext features; plaintext persistence is not an allowed temporary implementation | Approved by owner 2026-08-15 |
| `FND-012` | Permit an opt-in reduced-feature zero-knowledge/E2EE profile no earlier than M4: architecture at M0, no M1/M2 preview promise, and no claim before `P5-533`, `P5-534`, exact selected `P5-535` leaves, `P5-536`, the immutable release-scoped `QA-019-ZK-<release>-<manifest-generation>` evidence child, interoperability vectors, and independent assessment | Move it earlier only by changing the compatibility matrix, dependencies, and independent-review gates—not by relabeling managed encryption | Approved by owner 2026-08-15 |
| `FND-013` | Keep basic comments and notifications in M2 rather than M1 as currently mirrored in the roadmap; require import/restore, macro registry infrastructure, bulk administration, i18n catalog/pseudo-locale/RTL readiness, attachment extraction, operations, and an exact QA manifest before M4 as recorded in the matrix; real translated catalogs require separately approved translation/review leaves | Amend the roadmap, matrix, M1/M4 packages, and implementation ledger together before dispatch | Pending |
| `FND-014` | Adopt the Apache-compatible dependency allow/manual-review/deny policy, exact artifact-to-source mapping, notices/SBOM expectations, and no required proprietary/premium add-on | Replace the allowlist/exception process before any dependency is selected | Approved by owner direction 2026-08-15 |
| `FND-015` | Adopt the proposed security response targets, coordinated-disclosure process, safe-harbor expectations, and release-advisory content in [SECURITY.md](../../SECURITY.md) | Supply replacement response/disclosure rules before opening intake | Pending |
| `FND-016` | Adopt the proposed Code of Conduct scope, enforcement ladder, confidentiality, recusal, and appeal model | Supply a replacement conduct policy before outside contributions are accepted | Pending |
| `FND-017` | Select scoped public sharing for M2 and require `P3-313`, `P3-314`, `P3-316`, and applicable `QA-017` leaves before the beta claim | Defer the public routes explicitly and amend M2, the capability matrix, and dependency graph before approval | Pending |
| `FND-018` | Defer blog/news/announcements from every pre-M4 release to the M5 parity track through `P5-529` and `P5-527` | Prioritize it earlier only by adding its exact packages, QA, security, import, and operational evidence to the earlier milestone | Pending |
| `FND-019` | Retain browser print through `P2-215` at M1, but defer the asynchronous server PDF export service `P5-509` and its `QA-011` scope to M5 | Add `P5-509` and its isolated-renderer/security/accessibility evidence to M4 if server-generated PDF is required for 1.0 | Pending |

## Operational prerequisites

Policy text alone does not make an intake channel operational. These are **public-release
requirements**, not blockers for private/local MVP development, and are not satisfied by
approving the table above.

| ID | Required evidence | Current state |
|---|---|---|
| `OPS-001` | GitHub private vulnerability reporting enabled for both `StrangeQuark/odoc` and `StrangeQuark/odoc-react`, maintainer notifications configured, and a non-collaborator test received; or a published monitored private security address with access/retention/backup-owner policy | Disabled in both repositories as of 2026-08-14; owner authorization required before changing external settings |
| `OPS-002` | A monitored private conduct-report route, its access owners, retention policy, and a safe test | No contact supplied |
| `OPS-003` | A different independent moderator/appeal route that can handle a report about the project owner, plus a safe test | No moderator/contact supplied |
| `OPS-004` | Exact unmodified Apache License 2.0 text in both repository roots, with repository metadata consistent with `Apache-2.0` | Implemented in this change; validate checksum and metadata before public release |
| `OPS-005` | Final relative/external documentation link, whitespace, and policy consistency checks; focused signed-off local commit recorded in `IMPLEMENTATION_STATUS.md` | Current relative-link/anchor, whitespace, table, policy, roadmap-content, cryptography/transport, dependency-graph, and structure checks pass. All 39 external links were enumerated: 29 returned HTTP 200 and 10 official USPTO/WCAG endpoints returned automated-access HTTP 403, with no 404, 5xx, or timeout; those protected sources require source-specific/manual confirmation at the completion gate. The focused commit and completion-state rerun still wait for all preceding owner-controlled items |

A security email is required before the first public release even if GitHub private
reporting is used during development. Do not publish a personal address or name someone
as an independent moderator without their informed consent.

## Approval statement

The project owner should record an approval equivalent to:

> I approve the decisions recorded as approved in the 2026-08-15 foundation
> bundle. For `OPS-001`, I authorize exactly **[choose one: enable GitHub private
> vulnerability reporting and maintainer notifications in both `StrangeQuark/odoc` and
> `StrangeQuark/odoc-react`, then perform a safe non-collaborator test; or publish the
> monitored security address ________ under its documented access, retention, and
> backup-owner policy, then perform a safe intake test]**.
> I supply the private conduct-report route **________** and the separate independent
> moderator/appeal route **________**, confirm that their responsible people consent,
> and authorize publication only of route/identity details they explicitly approved for
> publication. I confirm `@StrangeQuark` as project owner and sole bootstrap maintainer.
> I retain **Odoc** as the current project name, acknowledge its documented collision
> risk, and require qualified legal review before public 1.0 release, trademark work,
> domain acquisition, or material commercial expansion. I understand
> that managed encryption is not E2EE and approve zero knowledge only as the separately
> gated optional profile described in `FND-012`.

The public-launch name review, security intake, conduct/appeal contacts, and release
checks remain required before a public release. They do not block private/local MVP
development or the architectural decisions in `P0-002`.

Record any rejection or modification by ID. A blanket “continue” does not authorize an
external repository-setting change, publish a private person's contact data, or resolve
a rejected legal/product choice.

## Completion record

Fill only after evidence exists:

| Field | Value |
|---|---|
| Owner approval date and durable reference | Pending |
| Approved/replaced decision IDs | Pending |
| Confirmed project owner/bootstrap maintainer | Pending |
| Retained name, public-launch qualified-review requirement, and later decision | Odoc retained for MVP development; review required before public 1.0/trademark/domain/commercial expansion |
| Security intake test evidence | Pending |
| Conduct/appeal consent and test evidence | Pending |
| Root license checksum/source | Pending |
| Validation commands/results | Pending |
| `odoc` commit | Pending |
| Ledger update | Pending |
