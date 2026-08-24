# Odoc

Odoc is a small, self-hosted documentation and notes app. Create spaces and pages, edit them in place, add media, search, discuss changes, and view a public GitHub README or selected JavaDoc source.

It is intentionally a simple Spring Boot + React + PostgreSQL application. Docker Compose is the supported way to run it locally.

## Quick start

From the folder that contains both `odoc` and `odoc-react`:

```bash
docker compose -f odoc/deploy/local/compose.yml -f odoc/deploy/local/compose.app.yml up --build
```

Open <http://localhost:8081>, create an email/password account, and start writing. Mailpit at <http://localhost:8025> shows local email. Press `Ctrl-C` to stop the stack; use `docker compose ... down` to remove containers while keeping your data.

The first start builds the API and frontend and creates persistent PostgreSQL and MinIO volumes. No cloud account, Kubernetes cluster, GitHub token, or object-store configuration is needed.

## Invite-only mode

Registration is open by default. To require a workspace invitation code for each new account:

```bash
ODOC_AUTH_INVITE_ONLY=true docker compose \
  -f odoc/deploy/local/compose.yml \
  -f odoc/deploy/local/compose.app.yml up --build
```

For an empty installation, create the first owner with invite-only mode off in a private environment, then restart with it on. Existing users can still sign in and recover their passwords.

## Useful local settings

All settings are optional. Set them in your shell or a Compose `.env` file before starting.

| Setting | Default | Purpose |
|---|---:|---|
| `ODOC_FRONTEND_PORT` | `8081` | Odoc web address |
| `ODOC_API_PORT` | `8080` | API port for local debugging |
| `ODOC_AUTH_INVITE_ONLY` | `false` | Require a workspace invite when creating an account |
| `ODOC_DATABASE_NAME` | `odoc` | Local PostgreSQL database name |
| `ODOC_DATABASE_USERNAME` / `ODOC_DATABASE_PASSWORD` | `odoc` / `odoc` | Local PostgreSQL credentials |
| `ODOC_MEDIA_BUCKET` | `odoc-media` | Local MinIO media bucket |

The default database and MinIO credentials are for a local machine only. Use HTTPS and deployment-specific secrets when exposing Odoc outside your private network.

## Reset local data

This removes Odoc’s local PostgreSQL and MinIO volumes permanently:

```bash
docker compose -f odoc/deploy/local/compose.yml -f odoc/deploy/local/compose.app.yml down --volumes
```

## Development checks

```bash
cd odoc
./mvnw --batch-mode test

cd ../odoc-react
pnpm test && pnpm run lint && pnpm run typecheck && pnpm run build
```

The checked-in OpenAPI snapshot is consumed by the frontend. Refresh it and generated frontend types whenever a public API changes.

## License

Odoc is licensed under [Apache License 2.0](LICENSE).
