# Local Compose dependencies

The default Compose stack is intentionally convenient for an isolated developer machine. The
TLS overlay is the development path that exercises trusted PostgreSQL, MinIO, and Mailpit
connections without ever committing a private key.

## Start with local TLS

1. Copy `.env.example` to `.env` and replace the development-only passwords.
2. Generate the ignored local CA and service certificates:

   ```bash
   ./deploy/local/scripts/bootstrap-pki.sh
   ```

3. Start the dependency and application stack:

   ```bash
   docker compose \
     --env-file deploy/local/.env \
     -f deploy/local/compose.yml \
     -f deploy/local/compose.app.yml \
     -f deploy/local/compose.tls.yml up --build
   ```

The TLS frontend becomes <https://localhost:8443>. Mailpit's UI becomes
<https://localhost:8025>; trust the generated CA only for this local environment.

The PostgreSQL TLS overlay rejects non-TLS TCP clients. API and worker use JDBC hostname and
CA verification. The frontend verifies the API certificate before proxying requests, and MinIO
bucket initialization also connects over HTTPS with the local CA.

To exercise both application images with an immutable root filesystem and only their declared
temporary writable paths, add `-f deploy/local/compose.hardened.yml` to that command. The API
and worker receive only `/tmp`; the frontend receives `/tmp`, Nginx cache, and Nginx runtime
state as `tmpfs` mounts. The runtime configuration is intentionally written to `/tmp`, not the
static asset layer.

For an isolated reproducible smoke of the PKI, TLS proxy, JDBC verification, and immutable
application containers (without touching the ordinary `odoc-local` stack), run:

```bash
./deploy/local/scripts/verify-secure-stack.sh
```

Set `ODOC_SECURE_SMOKE_THIN_SLICE=true` for the Phase 0 test-only validation/idempotency
endpoint. That profile is intentionally absent from normal Compose and production configuration.

To verify the minimal Helm contract without touching an existing Kubernetes cluster, run:

```bash
./deploy/helm/odoc/scripts/kind-smoke.sh
```

It creates an isolated `kind` cluster named `odoc-kind-smoke`, builds and loads the local
API/frontend images, installs disposable PostgreSQL plus the API, worker, and frontend,
checks health/API/deep-link routing, and deletes that cluster in all exit paths. This is a
chart-wiring smoke only: the Phase 0 in-cluster fixture intentionally does not claim the
production TLS/mTLS proof required before a production deployment.

## Reset local data

This is destructive and only targets the named `odoc-local` Compose volumes:

```bash
docker compose -f deploy/local/compose.yml -f deploy/local/compose.app.yml down --volumes --remove-orphans
rm -rf deploy/local/state/pki
```

Run `bootstrap-pki.sh` again before using the TLS overlay after a PKI reset. Never use this
Compose configuration, its credentials, or its CA in a production deployment.
