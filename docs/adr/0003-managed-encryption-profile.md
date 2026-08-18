# ADR-0003: managed application-layer encryption is the MVP profile

**Status:** accepted for private/local MVP engineering on 2026-08-15

The MVP security profile is **managed encryption**, not end-to-end encryption.
Odoc-controlled services may decrypt data only after authorization; this is
necessary for server-side rendering, search, GitHub synchronization, malware
scanning, recovery, and operational support. Transport encryption and
encrypted operator-managed database/object-store backups are required in a
production deployment, but the current local MVP deliberately has neither
production keys nor application-layer encrypted records. It must not be
marketed as encrypted or E2EE yet.

Before a sensitive persistence path is added, Phase 1 will provide a shared
envelope-encryption port. Its authenticated envelope binds a security-scope
kind and ID (instance/global-identity or workspace), resource ID, purpose,
schema version, and key version as additional authenticated data. The key
hierarchy uses an operator-managed root/KMS key to wrap purpose-scoped DEKs;
identity data has an instance scope and workspace data has a workspace scope.
Keys, secrets, plaintext exports, and decryption failures must never enter
logs or browser storage.

The Phase 0 feasibility boundary is exercised by the same versioned AES-256-GCM
test vector in the Java and browser repositories, plus a shared AES-KW key-wrap
vector. The record vector binds scope, resource, purpose, schema, and key version
in AAD and proves that either ciphertext or AAD tampering fails; the key-wrap vector
proves a browser and Java implementation unwrap the same DEK and reject tampering.
The exact data classification and feature rules are in
[the profile matrix](../security/data-classification-and-profile-matrix.md), while
[the envelope framing specification](../security/managed-envelope-format-v1.md)
defines the P1-115 implementation contract. These are deliberately not a production
encryption adapter: test keys are public and no current code supplies KMS wrapping,
persistence, rotation, or chunk encryption.

Search requires a separate, leakage-documented design: no plaintext `tsvector`
or document text is permitted in durable stores once the managed profile is
enabled. The initial candidate is per-workspace keyed blind indexes followed by
authorized bounded decrypt-and-rank. Its access-pattern, equality/frequency,
and chosen-plaintext leakage must be documented and tested before adoption.

Zero-knowledge/E2EE is deferred. It requires a reviewed browser key model,
opaque server content, feature compatibility declarations, and a release-
scoped independent verification gate. It cannot be enabled merely through an
environment variable, and it is incompatible with server-side full-text
search, generated GitHub/Javadoc views, and untrusted server-delivered
JavaScript guarantees unless their designs change.

The future signed capability manifest has a stable draft schema in
[zero-knowledge-capability-manifest-v1.schema.json](../security/zero-knowledge-capability-manifest-v1.schema.json).
The Java and browser test suites also verify the same test-only Ed25519 manifest
signature vector and reject a changed canonical payload. This demonstrates a
cross-runtime verification boundary only; the vector's public key, signature,
and release data cannot authorize a product capability.
P5-533 through P5-536 own its signing trust root, generation/revocation store,
runtime verifier, anti-rollback behavior, and release-scoped independent evidence.

The retained browser feasibility test proves only that a non-extractable
WebCrypto key can encrypt and decrypt a client-side payload without exporting
the raw key. It is not a deployed zero-knowledge protocol: it supplies no
device enrollment, recovery, membership epoch, distribution, collaboration,
malicious-origin defense, or product claim. Those remain P5-533 through P5-536.
