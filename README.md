# Odoc

`Odoc` is the current project and repository name. The
[collision research](docs/project/name-collision-research.md) identifies material naming
risk; the owner accepted that risk for private/local MVP development. This is not legal
clearance or a trademark claim. A qualified review is required before public 1.0,
trademark, domain, or material commercial decisions.

The documentation platform provides polished, searchable documentation and first-class
GitHub repository, README, and static Java API documentation.

The repository contains a local Spring Boot/PostgreSQL MVP with built-in email/password
accounts, secure cookie sessions, email verification, recovery, and workspace invitations.
OIDC/OAuth/SSO are optional future account-linking providers rather than a requirement for
normal sign-in.

## Local account enrollment

Local Compose allows normal email/password account creation by default. To make a
deployment invite-only, set the following before starting or recreating the API:

```bash
ODOC_AUTH_INVITE_ONLY=true docker compose \
  -f deploy/local/compose.yml \
  -f deploy/local/compose.app.yml up --build
```

In invite-only mode, existing users can still sign in, recover a password, and accept a
workspace invitation. New users must enter the one-time workspace invitation code in the
Create account form; consuming it creates the account and workspace membership atomically.
For Helm, set `api.auth.inviteOnly: true`. This is a local/private MVP enrollment policy;
production identity, mail delivery, and provider configuration need their own deployment review.

For a brand-new database, first create the initial owner in a restricted/private environment
with invite-only mode left off. Then restart with invite-only mode on and have that owner issue
the remaining workspace invitations. Odoc intentionally does not expose an unauthenticated
“first account wins” bootstrap endpoint, because that would defeat invite-only deployment.

## Project documentation

- [Capability and release matrix](docs/product/capability-matrix.md)
- [P0-001 foundation approval record](docs/product/foundation-approval-record.md)
- [Representative user journeys](docs/product/user-journeys.md)
- [Success metrics and product risks](docs/product/success-metrics-and-risks.md)
- [Open-source and dependency policy](docs/project/open-source-policy.md)
- [Working-name collision and replacement-name research](docs/project/name-collision-research.md)
- [Governance](GOVERNANCE.md)
- [Support, deployment, browser, accessibility, and retention policy](docs/project/support-policy.md)
- [Contributing](CONTRIBUTING.md)
- [Security reporting](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Name and trademark policy](TRADEMARKS.md)

## License

Odoc is licensed under the [Apache License 2.0](LICENSE). See
the [license decision](docs/project/open-source-policy.md#project-license-decision)
for rationale and dependency-policy requirements.
## Local backend checks

```bash
./mvnw --batch-mode verify
```

`verify` is self-contained through the Maven Wrapper. It writes the JaCoCo HTML
coverage report to `target/site/jacoco/index.html` and the CycloneDX dependency
SBOM (including dependency license metadata) to
`target/classes/META-INF/sbom/application.cdx.json`. Both are build artifacts;
neither is committed.

## Runtime roles

For local development, use the Compose commands in
[`deploy/local`](deploy/local/README.md); they start the API and worker roles
with PostgreSQL, MinIO, and Mailpit. The same application artifact also supports
the explicitly tested non-HTTP roles:

```bash
SPRING_PROFILES_ACTIVE=local,worker ./mvnw spring-boot:run
SPRING_PROFILES_ACTIVE=local,parser ./mvnw spring-boot:run
```

`parser` is intentionally inert until the later isolated repository-parser
work. Never use the `local` profile or its development credentials in a
production deployment.

The generated OpenAPI contract is available from a running API at `/v3/api-docs`.
`openapi/odoc-v1.json` is the checked-in contract snapshot consumed by the independent
frontend repository. Its adjacent `contract-manifest.json` pins the contract version and
SHA-256. Any API change must refresh the artifact, manifest, generated frontend types,
and the frontend's generated contract metadata in the same coordinated change.

`openapi/odoc-thin-slice-v1.json` is deliberately separate: it is generated only with
the `thin-slice` test profile and describes the P0 idempotency demonstration command.
It is checked by the corresponding PostgreSQL HTTP test and must never be merged into
the normal production API snapshot.
