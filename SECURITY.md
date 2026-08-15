# Security policy

Odoc handles private documentation, identity, repository integrations, and encryption
keys. Please report suspected vulnerabilities privately and avoid accessing data that
is not yours.

This policy is proposed during roadmap package `P0-001`. There is no supported Odoc
release yet; supported versions will be listed here when releases begin. As of
2026-08-14, private vulnerability reporting is disabled in both repositories, so the
intake described below is not operational and `P0-001` cannot be approved yet.

## Reporting a vulnerability

Once enabled and tested, use GitHub private vulnerability reporting for the affected
repository:

- [Odoc backend, deployment, and cross-cutting reports](https://github.com/StrangeQuark/odoc/security/advisories/new)
- [Odoc React frontend reports](https://github.com/StrangeQuark/odoc-react/security/advisories/new)

Do not put even a metadata-only vulnerability report in a public issue. Before this
policy is adopted, the owner must either enable both private-reporting endpoints with
maintainer notifications and verify them from a non-collaborator account, or publish a
monitored private security address with documented access, retention, and backup-owner
coverage. A dedicated monitored security address is additionally required before the
first public release; this document will not invent one prematurely.

Provide what you safely can:

- affected version, commit, image digest, chart version, and deployment topology;
- impact and the security boundary or data classification involved;
- minimal reproduction steps or a proof of concept using only data you control;
- relevant request IDs, sanitized logs, and environmental assumptions;
- whether the issue is known to be exploited or publicly disclosed;
- a safe way and preferred language for maintainers to contact you.

Do not send production credentials, private page/repository content, encryption keys,
personal data, or a database dump. Maintainers will arrange a safer transfer if an
artifact is essential.

## What happens next

The project aims to acknowledge a complete report within three business days and give
an initial severity/scope assessment within seven business days. These are response
targets, not a promise to ship an unsafe fix on a fixed schedule. Maintainers will:

1. limit access to the smallest qualified, unconflicted response group;
2. reproduce and classify the report without exceeding the reporter's authorization;
3. agree on communication cadence and coordinated-disclosure timing;
4. prepare tests, a fix, upgrade/mitigation guidance, and affected-version analysis;
5. request a CVE when appropriate and publish credit if the reporter wants it;
6. disclose enough detail for users to act after a fix or mitigation is available.

Ninety days is the default coordinated-disclosure target, but active exploitation,
vendor dependencies, user safety, incomplete fixes, or reporter needs can justify a
different date. The project will not threaten good-faith reporters for accidental,
minimal interaction performed to confirm a vulnerability.

## Scope

Reports are welcome for source code, APIs, browser behavior, official containers,
Compose/deployment definitions, Kubernetes charts, build/release automation, imports,
parsers, repository integrations, authentication/authorization, tenant boundaries,
cryptography, key recovery, backups, logs, and dependency/supply-chain behavior.

Especially urgent examples include:

- cross-workspace, restricted-page, private-repository, attachment, or key disclosure;
- authentication/session bypass, privilege escalation, forged public shares, or IDOR;
- stored/reflected XSS or unsafe rendering of editor, README, JavaDoc, import, macro,
  search-highlight, filename, or URL data;
- CSRF, SSRF, webhook forgery, token leakage, parser sandbox escape, or execution of
  repository-controlled code;
- plaintext classified data in persistent stores under a managed-encryption profile,
  or any false zero-knowledge/E2EE property;
- destructive migration, backup/restore, concurrency, or autosave behavior that can
  lose acknowledged user data;
- compromised release, dependency, build runner, signing key, image, or chart.

General hardening suggestions without a concrete impact, vulnerability scanner output
without validation, social engineering, denial-of-service traffic against systems you
do not own, physical attacks, and issues solely in unsupported modified deployments may
be handled as normal issues or closed. This is not a bug-bounty promise.

## Safe-harbor expectations

Act in good faith, stay within accounts and systems you own or have explicit permission
to test, minimize collection, stop after confirming impact, do not persist or disclose
other people's data, do not degrade service, and allow a reasonable remediation window.
Comply with applicable law. The project will not recommend legal action for research
that follows this policy, but it cannot authorize testing of third-party systems or bind
other organizations.

## Supported versions

| Version | Supported |
|---|---|
| No public release yet | No production support claim |

The release support window will follow
[docs/project/support-policy.md](docs/project/support-policy.md) and will be updated
before 1.0. Forks and modified builds should reproduce on an official version when
possible; maintainers will still assess an issue that plausibly affects upstream.

## Security updates and advisories

Security fixes receive regression tests that avoid embedding live exploit secrets or
private data. Advisories identify affected and fixed versions, severity rationale,
workarounds, required rotations or re-encryption, migration/rollback consequences, and
known detection signals. Release artifacts must pass the project's signing, provenance,
SBOM, vulnerability, and reproducibility gates before they are called official.
