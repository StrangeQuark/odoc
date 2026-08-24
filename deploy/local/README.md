# Run Odoc locally with Docker Compose

Run this command from the parent directory containing `odoc` and `odoc-react`:

```bash
docker compose -f odoc/deploy/local/compose.yml -f odoc/deploy/local/compose.app.yml up --build
```

Then open <http://localhost:8081>. The stack contains only what the local app needs:

- `frontend`: the Odoc web app;
- `api`: the Spring Boot application;
- `postgres`: page, account, and workspace data;
- `minio`: local media storage;
- `mailpit`: local email preview at <http://localhost:8025>.

PostgreSQL and MinIO use named Docker volumes, so restarting or stopping the stack preserves Odoc data.

## First use and invite-only mode

Create an email/password account at the Odoc web address. Registration is open by default.

To require a workspace invitation code for each new account, start it with:

```bash
ODOC_AUTH_INVITE_ONLY=true docker compose \
  -f odoc/deploy/local/compose.yml \
  -f odoc/deploy/local/compose.app.yml up --build
```

For a new install, create the first owner while this is off in a private environment; then turn it on and use that owner to make invitations.

## Smoke checklist

After the containers are up, verify the product in this order:

1. Register and sign in.
2. Create a workspace, a space, and a page.
3. Edit the page in place, add a heading, list, link, and image, then save and refresh.
4. Add a comment, open history, and restore a revision if desired.
5. Search for text in the page from the header.
6. Attach a public GitHub repository, refresh it, and read its README.
7. In that repository card, enter one relative Java path such as `src/main/java/example/Guide.java` and load the JavaDoc view.

The JavaDoc feature reads only the selected public source file; it does not clone, build, or run repository code.

## Stop or reset

Stop containers and keep data:

```bash
docker compose -f odoc/deploy/local/compose.yml -f odoc/deploy/local/compose.app.yml down
```

Reset all local Odoc data (destructive):

```bash
docker compose -f odoc/deploy/local/compose.yml -f odoc/deploy/local/compose.app.yml down --volumes
```

The `compose.tls.yml` and hardening overlays are development experiments, not part of the supported lightweight MVP path.
