# ADR 0004 — Phase 0 vertical-slice architecture review

**Status:** approved by the project owner on 2026-08-17 as Phase 0 test infrastructure only  
**Scope:** `P0-014`; this is not a product-architecture approval for the later MVP.

## Decision proposed for approval

Approve the Phase 0 vertical slice as **test infrastructure only**. It boots a separate
no-database Spring application and proves the shared transport and deployment seams
before the tenancy, authorization, document, and account domains are implemented. It
must not be reused as a production command API or used to justify the current Basic-auth
development adapter as a production identity design.

## Bounded surface

With the explicit `thin-slice` profile enabled, the separately versioned contract exposes
only:

- `GET /api/v1/system/info`
- `POST /api/v1/test/commands/echo`

The command proves request validation, RFC 9457 problem mapping, request IDs,
`Cache-Control: no-store`, idempotent replay, and divergent-key conflict handling. It
has no domain migration, workspace, page, attachment, or identity model. Its controller
is profile-gated; the normal OpenAPI contract test proves the test command is absent.
The replay store is intentionally process-local because this endpoint is disposable test
infrastructure, not a production command API. Its Compose proof therefore runs one API
replica; the full `P0-013` stack separately proves that the stateless system route
survives a two-replica failover. The smoke script rejects the misleading combined mode.

## Evidence reviewed

- `ThinSliceApiIntegrationTest` compares the generated thin-profile OpenAPI document
  with `openapi/odoc-thin-slice-v1.json`, performs validation/replay/conflict requests,
  and proves that the context has no `DataSource`, Flyway/JPA domain services, page
  service, or space service.
- `PostgresApiIntegrationTest` compares the normal contract with `openapi/odoc-v1.json`
  and rejects any appearance of `/api/v1/test/commands/echo`.
- The React generated thin client and MSW fixtures run through the same transport/error
  mapper as the ordinary contract.
- `ODOC_SECURE_SMOKE_BUILD=false ODOC_SECURE_SMOKE_THIN_SLICE=true \
  ./deploy/local/scripts/verify-secure-stack.sh` passed on 2026-08-15; it exercised the
  profile through the disposable TLS Compose proxy without starting the worker or the
  media exercise, proved PostgreSQL has zero public tables, then cleaned up its resources.
  The ordinary secure-stack smoke retains the full worker/media/persistence proof under
  `P0-013`.

## Required approval and guardrails

The approving reviewer must record their identity/date in `IMPLEMENTATION_STATUS.md` and
confirm all of the following:

1. Feature work starts with the `M0-GATE` dependency order, not from the test endpoint.
2. Built-in invite-only accounts and secure cookie sessions are implemented in `P1-101`;
   shared Basic authentication stays development-only.
3. The endpoint remains excluded from the normal runtime/contract and can be deleted once
   its transport behavior is covered by real authenticated commands.
4. No page, workspace, or authorization behavior is inferred from this test slice.

The owner approved this bounded test-infrastructure use on 2026-08-17. This approval
does not approve it as a production identity or product-domain API.
