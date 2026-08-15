# Contributing to Odoc

Thank you for helping build Odoc. The project welcomes bug reports, design
feedback, documentation, tests, translations, and code from people of every
experience level.

This policy is proposed during roadmap package `P0-001` and takes effect when
the project owner approves the foundation policies.

## Before starting

- Read [GOVERNANCE.md](GOVERNANCE.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md),
  and the [security policy](SECURITY.md).
- Search the relevant repository for an existing issue or pull request.
- Discuss a large feature, public contract, migration, cryptographic change,
  dependency, or architecture change before investing in implementation.
- Never put vulnerability details, credentials, private repository data, or
  personal data in an issue, pull request, fixture, screenshot, or log. Use the
  private process in [SECURITY.md](SECURITY.md).

Odoc is being delivered through small, dependency-ordered roadmap packages.
When an issue assigns a package or leaf ID, keep the change within its stated
paths, deliverables, non-goals, and acceptance criteria. A parent epic that spans
repositories must be split into repository-owned leaves before implementation.

## Development workflow

1. Fork the relevant repository and create a focused branch.
2. Make the smallest coherent change that satisfies the agreed issue or package.
3. Add or update tests, documentation, migrations, API schemas, accessibility
   evidence, and operational notes as the change requires.
4. Run every validation command documented by that repository from a clean
   checkout. During the foundation phase, do not invent commands that have not
   yet been added to the repository.
5. Review the diff for secrets, generated noise, unrelated formatting, copied
   material, and dependency or license changes.
6. Sign off every commit under the Developer Certificate of Origin.
7. Open a pull request that links its issue/package and supplies the evidence in
   the checklist below.

Application scaffolding and authoritative commands will be added by the Phase 0
bootstrap packages. Once present, each repository README and CI workflow is the
source of truth for local equivalents of required checks.

## Developer Certificate of Origin

Odoc uses the [Developer Certificate of Origin 1.1](https://developercertificate.org/)
and does not require a Contributor License Agreement. Add a sign-off to each
commit with:

```text
git commit --signoff
```

The sign-off makes the certifications in the full DCO text linked above, including
the relevant origin, license, and right-to-submit representation. Contribution does
not assign copyright to Odoc; rights remain with the applicable copyright holder,
which may be an employer or other authorizing party. Do not add another person's
sign-off unless they made it or expressly authorized you to record it.

## Pull request expectations

A reviewable pull request states:

- the problem, package/leaf ID, scope, and explicit non-goals;
- owned files and any coordination required in the other repository;
- user-visible behavior and compatibility impact;
- API, editor-schema, database migration, configuration, security, privacy,
  accessibility, encryption-profile, and operational changes;
- exact validation commands and results, with redacted artifact links where useful;
- new dependencies, their purpose, license, provenance, and bundle/image impact;
- rollout, backward compatibility, rollback or forward-fix plan, and remaining risk;
- screenshots or recordings for visual changes, without private or personal data.

Generated code, lockfiles, migration files, OpenAPI roots, editor registries, and
shared deployment helpers will receive designated owners in the architecture/bootstrap
packages. Once assigned, do not regenerate or edit them concurrently without
coordination. Generated output must be reproducible in an isolated checkout and
reviewed like authored code.

## Quality requirements

- Backend authorization is authoritative; hiding a control in the browser is not
  a security boundary.
- Treat editor content, imports, README files, JavaDoc, search highlights, URLs,
  filenames, and integration responses as untrusted input.
- Preserve tenant isolation and the workspace encryption profile in every storage,
  cache, search, export, log, and background-job path.
- New interactive behavior needs a keyboard path and appropriate automated and
  manual accessibility evidence against the project's WCAG 2.2 AA target.
- Tests should assert behavior and failure recovery, not merely implementation
  details or broad snapshots.
- Never weaken a gate to make a change pass. If a gate is wrong, change it in a
  separate, justified review.

## Dependencies and third-party material

Follow the [open-source and dependency policy](docs/project/open-source-policy.md).
Do not paste code, documentation, media, fonts, datasets, or fixtures without
recording their source and compatible license. A package-registry license label or
AI-generated attribution guess is not sufficient provenance.

## Review and merge

After the one-time `P0-001` adoption exception, every change requires the unconflicted
approval specified in [GOVERNANCE.md](GOVERNANCE.md), and authors do not approve their
own changes. Ordinarily a maintainer supplies the required approval; the governance
document's narrow bootstrap transition instead uses public unconflicted external reviewers
for owner-authored changes until a second maintainer is appointed. Security boundaries,
cryptographic protocols, destructive migrations, release automation, and license-policy
changes also require the specified specialist review. Maintainers may request that a large
pull request be split even when its behavior is correct.

By participating, contributors agree to follow the
[Code of Conduct](CODE_OF_CONDUCT.md).
