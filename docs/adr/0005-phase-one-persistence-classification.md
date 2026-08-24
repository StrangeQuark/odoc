# ADR 0005: Phase 1 persistence classification and recovery limits

Every newly introduced durable string, JSON payload, or blob is classified by its
owning package in `PersistenceClassificationRegistry`. `ENCRYPTED` values must use
the managed envelope at their first write; `ROUTING` values are the deliberately
minimal plaintext needed for authorization, query routing, quotas, or rendering
policy. Ciphertext is not SQL-sortable or searchable. Equality lookup is permitted
only through a purpose-separated keyed token maintained by the owning package.

`media_assets.filename` and `media_assets.content` are explicit legacy backfill
columns. New media writes use `filename_envelope` plus encrypted S3-compatible
objects. A production promotion must run and verify the backfill before removing the
legacy columns; raw backups/dumps without the matching wrapping-key recovery material
are intentionally unrecoverable. Key recovery evidence is therefore a release gate,
not an optional operational task.
