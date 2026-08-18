# Data classification and encryption-profile matrix

**Owner:** P0-016  
**Status:** design baseline for the managed-encryption implementation in P1-115  
**Current-product warning:** the current local MVP persists page and media data in plaintext. This document is a build contract, not a statement of current protection.

## Classification and boundary

C0 is deliberately public product metadata. C1 is operational or routing metadata
that is access-controlled and minimized, but may be needed to route an authorized
request. C2 is confidential customer, credential, or security data. No C2 value may
be intentionally written to a durable store in plaintext after the managed profile is
available. The only routine plaintext locations are authorized process memory and the
recipient device after a deliberate display, download, print, mail, webhook, or
provider disclosure.

Managed means Odoc services may decrypt C2 after authorization. It is not E2EE.
Zero knowledge is a later, separately reviewed option; it cannot be enabled merely
by configuration and must reject unsupported server-side features.

| Data / sink | Class | Managed profile after P1-115 | Zero-knowledge profile | Allowed persistent plaintext |
| --- | --- | --- | --- | --- |
| Page/draft/version content, comments, titles, labels | C2 | Purpose-scoped encrypted envelope; authorized plaintext only in memory | Opaque client ciphertext | None |
| Media/object bytes and repository snapshots | C2 | Chunk-encrypted object format | Client ciphertext; server cannot inspect or thumbnail | MIME/length limits and opaque IDs only |
| Password verifiers, session/CSRF secrets, API tokens, OAuth tokens | C2 | One-way verifier or purpose-scoped envelope; never log | Same for server authentication material | None |
| User account / external identity | C2 | Instance/global-identity scope, never a workspace key | Instance identity remains needed for account access | Stable opaque IDs and approved lookup tokens only |
| Membership/ACL/restrictions | C2 | Encrypted records; authorization evaluation may temporarily decrypt | A separate reviewed protocol is required | Opaque IDs and minimum role/routing state |
| Search source / snippets / query text | C2 | Keyed blind-token candidate index plus bounded authorized decrypt/rank | Disabled pending reviewed protocol | Leakage-documented blind tokens and opaque cursor data |
| Jobs/outbox/audit events | C2 | Encrypted payloads; immutable audit structure | Disabled or client-safe relay only | Event type/time, opaque IDs, retry state |
| GitHub credentials/webhooks | C2 | Dedicated integration purpose scope, bounded cache | Disabled | Repository binding ID and permitted public metadata |
| Logs, traces, error reports, analytics | C1/C2 | Redact C2 before emission; no durable spool unless encrypted | Same | Request/deployment IDs and approved C0/C1 fields |
| Browser storage and HTTP/service-worker cache | C2 | No durable storage by default; recovery only under approved client-key design | Encrypted client ciphertext only | Nothing classified; use no-store for decrypted responses |
| Temp files, proxy/JVM buffers, parser staging, swap/core dumps | C2 | Memory-backed or independently encrypted; bounded cleanup | Same | None |
| PostgreSQL/WAL/replicas/backups/object-store backups | C2 | App/provider envelope plus encrypted backup media and separate recovery-key authority | Opaque client ciphertext plus encrypted backup media | Schema, opaque IDs, approved C1 metadata |

## Key scopes and mandatory purpose separation

The root/KMS authority wraps non-exportable or short-lived data-encryption keys
(DEKs). Odoc must not implement a custom cipher, KDF, or wrapping primitive.

    operator KMS/HSM root
    ├── instance/global-identity scope
    │   ├── identity lookup / account profile
    │   └── session and authentication material
    ├── workspace scope
    │   ├── page and comment content
    │   ├── media / object chunks
    │   ├── search-token and search-candidate material
    │   ├── jobs/outbox/audit payloads
    │   └── repository snapshots
    └── future user/device scope (only where reviewed recovery requires it)

An envelope authenticates scope kind plus stable scope ID, resource ID, purpose,
schema version, and key version as associated data. A global user cannot be silently
put under an arbitrary workspace key. Lookup tokens have a distinct purpose and key
from stored fields, so a token never decrypts a record or crosses purposes.

## Feature compatibility

| Capability | Managed | Zero knowledge | Owner / decision |
| --- | --- | --- | --- |
| Page view/edit, comments, history | Supported after encrypted schema work | Client implementation required | P1-115, P2-206 through P2-213, P5-533 |
| Full-text search | Supported only via documented leakage-bounded design | Disabled pending separate protocol | P3-300 through P3-303 |
| Media upload/display | Supported after encrypted streaming object adapter | Client encrypt/decrypt and thumbnailing required | P1-110, P2-216, P5-533 |
| GitHub README/Javadoc sync | Supported | Disabled | P4-400 through P4-423 |
| Server export/PDF/preview/analytics | Deliberate authorized release only | Disabled or client-only | Owning Phase 5 package |
| Collaboration | Supported when relay architecture is reviewed | Separate CRDT/key-epoch protocol required | P5-500 and P5-533+ |
| Public shares | Managed capability sessions only | Separate recipient-key protocol required | P3-313 through P3-316, P5-533+ |

Every new persistence package must record its C0/C1/C2 fields, scope, purpose,
envelope version, authorized plaintext releases, and negative raw-store test in its
ledger row before implementation begins. A package without that declaration is not
ready for Phase 1 implementation.

## Operator prerequisites and non-goals

Production operators must provide TLS with hostname/CA validation on declared service
hops, a KMS/HSM or approved secret-store boundary, encrypted database and object-store
backups, non-plaintext swap/hibernation, redacted encrypted telemetry spools (or no
durable spool), and encrypted Kubernetes secret storage or an external secret manager.
Native base64-only Kubernetes Secrets are not an encrypted-persistence guarantee.

This document does not promise confidentiality after a user exports, prints, or shares
data, nor in recipient email/browser/device backups. It also cannot protect a
zero-knowledge web client from malicious future JavaScript served by a compromised
application origin; that limitation must accompany any future E2EE claim.
