# Success metrics and product risks

**Status:** proposed foundation policy; project-owner approval is required before
`P0-001` is complete.

**Roadmap package:** `P0-001`

**Last reviewed:** 2026-08-14

Odoc should be useful without turning private documentation into surveillance data.
Server operators may enable privacy-reviewed aggregate product telemetry, but it is off
by default. Operational health metrics must not contain page bodies, search terms,
repository source, user/page/repository identifiers as metric labels, or stable behavioral
profiles. Self-hosters own their telemetry configuration and retention.

## Initial success measures

| Outcome | Measure | Initial decision threshold |
|---|---|---|
| Successful evaluation | Fresh supported environment reaches the documented thin slice | 100% of release qualification installs; failures have actionable diagnostics |
| Authoring durability | Acknowledged draft/publish operations lost in fault and upgrade suites | Zero |
| Tenant confidentiality | Confirmed cross-workspace or restricted-content disclosure | Zero; any occurrence is a release blocker and security incident |
| Encryption coverage | Classified plaintext canaries found in managed-profile persistent stores | Zero at the applicable release gate |
| Transport encryption | Declared production application/dependency/management hops that permit plaintext, skip hostname/CA verification, or downgrade | Zero; per-hop negative tests and packet-capture canaries pass |
| Search usefulness | Reviewed relevance corpus and top-result expectations | Meet the approved `QA-009` ordering ranges; no authorization leakage |
| Search/sync freshness | Projection and GitHub target-to-visible lag | Set after reference benchmarks; queue age has an alert and runbook |
| GitHub correctness | Visible snapshot components referring to different SHAs | Zero |
| Parser safety | Repository-controlled process, bytecode/class loading, egress, credential read, or out-of-mount write | Zero |
| Accessibility | Serious/critical automated or release-manual findings | Zero unresolved; lower findings have owner and target |
| Portability | Supported native archive restored with declared content/checksums | 100% of deterministic release fixtures |
| Recoverability | Scheduled restore drill meets approved RPO/RTO | 100% before GA; results record exact environment and gaps |
| Upgrade safety | Supported-version upgrade fixture and declared rollback/forward-fix path | 100% before release |
| Operational diagnosis | Actionable alerts with a tested linked runbook | 100% of release alerts |

Latency, throughput, bundle, repository, document, and tenant-size numbers are not
production promises until measured. The provisional qualification targets below give
`P0-002` an architecture budget and `P6-610` a falsifiable reference benchmark. That
package must record hardware, topology, data shape, concurrency, warm-up, dependency
latency, and error rate; it may revise a target only through an approved decision, not
by silently shrinking the dataset.

## Provisional service and capacity targets

These targets require project-owner approval with the rest of `P0-001`. Latencies are
server-observed for successful non-streaming requests under the reference workload,
excluding an intentional client debounce but including authorization and storage work.
The M4 release manifest replaces “provisional” with measured supported values.

### Availability classes

| Deployment class | Initial objective |
|---|---|
| Developer Compose | Deterministic development and test only; no availability objective or production claim |
| Single-node community | Recoverable self-hosting, no HA/SLA claim; planned maintenance and a host/dependency failure can make the entire instance unavailable |
| HA Kubernetes reference | Candidate 99.9% monthly end-user core-request availability, excluding announced maintenance; failures of Odoc, required PostgreSQL, object storage, or KMS count in the denominator even when operator-supplied. New-login availability reports IdP failure separately; GitHub/mail workflows have separate objectives and cannot reduce already-authorized page-read availability. Page reads continue from safe last-good data during nonessential worker/integration failure. |

### Interactive API latency budget

| Operation | p50 | p95 | p99 |
|---|---:|---:|---:|
| Published page read | 100 ms | 300 ms | 1,000 ms |
| Visible page-tree expansion | 150 ms | 400 ms | 1,200 ms |
| Permission-filtered search | 150 ms | 500 ms | 1,500 ms |
| Draft autosave, excluding debounce | 200 ms | 750 ms | 2,000 ms |
| Publish a normal page version | 300 ms | 1,200 ms | 4,000 ms |
| Create/reply to a comment | 150 ms | 500 ms | 1,500 ms |
| Negotiate an attachment upload | 150 ms | 500 ms | 1,500 ms |
| Browse/search an already-published API-doc snapshot | 150 ms | 500 ms | 1,500 ms |

All endpoints also need an error-rate objective, timeout below the edge timeout, bounded
query count, and cancellation behavior. Large export/import, upload bytes, reindexing,
repository sync, parsing, and encryption rotation are asynchronous and excluded from
these request latencies.

### Initial supported data guardrails

| Dimension | Qualification target or default |
|---|---|
| Canonical editor document | 2 MiB encoded JSON, 50,000 nodes, maximum structural depth 64; graceful read/export fallback beyond editor performance limit |
| One table block | 200 rows × 50 columns; larger structured datasets belong in the separately gated database/table product |
| Attachments | 100 MiB default per object, 100 attachments per page; operator may lower limits, and raising them requires storage/scan/timeout validation |
| Reference workspace | 100,000 pages/versions-in-scope corpus, 5,000 members, 2,000 groups, and 1,000 spaces with mixed restrictions |
| Reference instance search | Ten reference workspaces and 1,000,000 authorized page/attachment/repository-document projections, with skewed sizes and ACLs |
| Browser page tree | 1,000 visible siblings and a 100,000-page lazily loaded tree without loading unauthorized titles or retaining unbounded nodes |

The API returns a typed size/complexity problem before accepting unsupported input.
Limits are configurable only within tested bounds and are enforced before decompression,
parsing, allocation, indexing, rendering, or encryption amplification can exhaust a pod.

### Asynchronous freshness and containment

| Workflow | Initial objective or hard bound |
|---|---|
| Page/search projection | p95 visible within 10 seconds; current authorization is never delayed behind the projection |
| Full reindex | 100,000-page reference workspace within 60 minutes without blocking reads/writes |
| GitHub webhook acknowledgement | p95 under 1 second after durable dedupe/job commit |
| GitHub target-to-visible metadata/README | p95 under 5 minutes absent provider throttling |
| Java API-doc target-to-visible | p95 under 15 minutes for the reference repository; last complete snapshot remains readable on failure |
| Repository input | 250 MiB compressed, 2 GiB expanded, 100,000 entries, path/depth/ratio guards, and ten-minute hard parser wall time unless a lower safe format limit applies |
| Repository fairness | At most two active sync generations per workspace and one publisher per binding; global concurrency follows measured DB/object/provider budgets |
| Durable jobs | Interactive queue oldest age alerts at 60 seconds, normal background at 10 minutes, and dead-letter/security notification within 15 minutes of terminal classification |

Provider throttling and a deliberately queued bulk job are reported separately rather
than discarded from measurements. Every missed objective has an oldest-age metric,
bounded retry/dead-letter behavior, and a runbook before M4.

### Durability, recovery, and cryptographic operations

| Area | Initial production-reference target |
|---|---|
| Acknowledged authored mutations | Zero loss in fault, upgrade, failover, and restore suites |
| HA database/object recovery point | RPO at most 15 minutes, with version-consistent database, object, and key material |
| Isolated full-service recovery | RTO at most 4 hours for the reference dataset; external side effects remain disabled until validation |
| Backup retention | At least 14 days PITR plus 35 daily recovery points; longer legal/business retention is operator policy and key retention must cover it |
| KMS dependency | Candidate 99.9% monthly availability and p95 operation latency under 250 ms on the reference provider; fail closed without corrupting acknowledged content |
| Decrypted data-key cache | Five-minute maximum starting default, process-memory only, bounded entries, zero persistent/log/telemetry copies |
| Managed-key rotation | New writes use the new key immediately; reference workspace envelopes rewrapped within 24 hours and full instance within seven days with resumable progress |
| Key recovery | Required keys restored and validated within the four-hour service RTO; loss produces an explicit unrecoverable result, never silent replacement |
| Crypto-shred/key deletion | Minimum 30-day approval delay and only after live data, holds, backups, replicas, exports, rollback, and restore impact are proven |

The formal backup and retention policy remains
[the support policy](../project/support-policy.md). These numbers do not imply that
deleting a live row instantly removes protected backups.

### Zero-knowledge architecture budget

The M0 ADR must prove a design for five enrolled devices per member, 1,000 members and
5,000 active device grants in a workspace, a 10,000-page/2 GiB client-side search index,
and a membership epoch change completing within 15 minutes on the reference client and
network. These are design inputs, not a shipping promise. `P5-533`, `P5-534`, the
exact selected `P5-535` leaves, `P5-536`, and the immutable release-scoped
`QA-019-ZK-<release>-<manifest-generation>` evidence child must measure them before
enabling the profile.

The accepted server-visible metadata must be enumerated in the ADR and user UI: at
minimum instance/workspace identifiers, membership/device grants, ciphertext sizes,
versions/timing, request/network metadata, and explicitly selected public-share routing.
Titles, bodies, comments, attachment bytes, recovery keys, local search terms, and
plaintext derived content are not accepted leakage. Traffic-analysis resistance is not
claimed unless separately implemented and assessed.

### Browser performance budget

On the reference mid-tier mobile/desktop profiles, target p75 LCP at most 2.5 seconds,
INP at most 200 ms, and CLS at most 0.1. The authenticated shell initial-route compressed
JavaScript target is 250 KiB; editor and repository/API-doc code is route-lazy, with each
initial feature-route compressed JavaScript target at most 500 KiB. Exceptions require
a measured user benefit, dependency/license review, and a dated removal or rebaseline
decision. Accessibility and correctness are not traded away to meet a byte budget.

## Optional product signals

If a deployment explicitly enables aggregate telemetry, prefer event counts that answer
whether a feature works: workspace setup completed, first page published, search returned
an opened result, import completed, sync completed, and restore test completed. Apply short
retention, minimum cohort sizes, documented bot filtering, opt-out, and no cross-workspace
comparison. Never collect content, query text, document titles, private URLs, or keystrokes.

## Product risk register

| Risk | Early control and decision gate |
|---|---|
| “Confluence parity” becomes unbounded | Capability matrix, explicit non-goals, package IDs, and `P5-527` claim audit |
| Project license discourages an intended contributor/deployer group | Owner approval of `AGPL-3.0-or-later`, compatibility automation, and review before public release |
| A conflicted working name creates user confusion, legal exposure, or an expensive late rename | Keep Odoc a temporary repository locator; select only a candidate at `P0-001`; complete expanded ecosystem/common-law/trademark screening and appropriate qualified review before `P0-002` freezes branding; repeat review before release and material expansion |
| Editor choice needs proprietary extensions or traps data | `P0-009` OSS comparison, Odoc-owned versioned schema, deterministic migrations/export |
| Tenancy is retrofitted after features | `P1-104`/`P1-113` before content breadth and `QA-008` for every resource path |
| Rich text, README, JavaDoc, macros, or highlights execute content | Structured models, allowlisted renderers, hostile corpus, sanitizer defense, CSP |
| Managed encryption is marketed as E2EE | Separate profiles and claim language; `P5-536` plus its immutable release-scoped `QA-019-ZK-<release>-<manifest-generation>` evidence child before any E2EE claim |
| Encryption prevents useful search/integrations or leaves plaintext derivatives | Field classification, purpose-separated encryption, explicit zero-knowledge compatibility matrix |
| TLS terminates before an unencrypted internal/dependency hop | Explicit transport matrix, generated local PKI, verified hostname/CA, rotation and packet-capture tests; NetworkPolicy is not treated as encryption |
| Key loss makes backups useless | Versioned envelopes, protected key backup, restore drills, delayed deletion, honest unrecoverable E2EE state |
| GitHub credentials/private source leak | GitHub App, short-lived server token, revocation generations, proxy private assets, redaction tests |
| Hostile repository escapes/exhausts workers or leaves plaintext staging | No builds; credential-free, egress-denied, bounded parser workload with hard kill, encrypted persistent stages, and canary-verified ephemeral plaintext cleanup |
| PostgreSQL becomes the scaling bottleneck | Connection budget including surge/jobs, measured queries, independent API/worker caps |
| Autosave or collaboration loses authored work | Idempotency, revisions, local recovery, conflict UX, fault/two-browser tests |
| Accessibility is treated as an automated checkbox | WCAG 2.2 AA plus keyboard, zoom/reflow, forced-color, and screen-reader release work |
| Backups exist but are not usable | Isolated database/object/key restoration with integrity smoke and measured RPO/RTO |
| Imports silently lose unsupported constructs | Versioned fixture matrix, dry run, explicit placeholders, compatibility report |
| Plugin ecosystem creates arbitrary execution | Trusted typed bundled registry first; separate sandbox/permission decision for extensions |

## Review cadence

- Revisit metrics and risks at every milestone and after a security/data-loss incident.
- Record owners and numeric SLOs once reference measurements exist.
- Delete metrics that do not drive a documented decision or operational response.
- Publish known limitations with the capability matrix; do not turn a failed target into
  a hidden exception.
