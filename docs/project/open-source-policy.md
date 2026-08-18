# Open-source and dependency policy

**Status:** owner-approved for MVP development on 2026-08-15

**Roadmap package:** `P0-001`

**Last reviewed:** 2026-08-14

This is a project policy, not legal advice. A qualified reviewer should confirm
license compatibility before the first public distribution and whenever a novel
license or linking model enters the product.

## Project license decision

The license for Odoc source, tests, build/deployment definitions, and project
documentation is **Apache License 2.0**, using the SPDX expression `Apache-2.0`.

Rationale:

- It is an [OSI-approved license](https://opensource.org/license/apache-2-0) with a
  clear [SPDX expression](https://spdx.org/licenses/Apache-2.0.html) and an explicit
  patent grant.
- It permits broad self-hosting, commercial use, modification, and redistribution
  without making an external service operator's source-release choice a condition.
- It remains compatible with the project's requirement that core functionality not
  depend on proprietary or paid-only add-ons.

Consequences:

- Distributions must preserve Apache-required notices and license text. Modified files
  must carry prominent change notices where the Apache license requires them.
- Contributors must have the right to submit their work under `Apache-2.0`. No
  contribution can add a hidden proprietary restriction to required core code.
- Dependency and asset terms still require review; permissive project licensing does
  not make incompatible or source-unavailable dependencies acceptable.
- This policy does not grant rights to the Odoc name or logos; see
  [TRADEMARKS.md](../../TRADEMARKS.md).

Alternatives considered:

| Alternative | Benefit | Reason not selected as the initial recommendation |
|---|---|---|
| AGPL-3.0-or-later | Network copyleft | Not selected: the owner chose a permissive license for broad adoption and self-hosting |
| MPL-2.0 | File-level copyleft and commercial friendliness | Not selected: Apache's simpler permissive model and patent grant fit the current project direction |
| AGPL-3.0-only | Fixed legal text | Not selected with the AGPL family |
| Dual AGPL/commercial | Can fund a vendor and accommodate proprietary embedding | Requires copyright ownership/CLA and commercial governance not approved for the community project |

No dual-license or Contributor License Agreement exists. The project uses a
Developer Certificate of Origin sign-off process unless governance explicitly
changes it. Contribution does not assign copyright to Odoc; rights remain with
the applicable copyright holder, which may be a contributor's employer or another
party that authorized the submission.

## Required notices

- Each repository root contains the unmodified Apache License 2.0 text.
- Build metadata, package manifests, source headers where appropriate, container
  labels, release archives, and SBOMs use `Apache-2.0` consistently.
- Third-party notices ship with every release artifact.
- Vendored, generated, fixture, font, image, icon, and dataset material carries its
  own provenance and license; the project license does not erase upstream terms.
- Every released binary, image, chart, package, and generated asset maps to an exact
  immutable source tag/commit plus the build scripts, interface definitions, dependency
  lockfiles, installation information, and other Corresponding Source required by the
  applicable licenses. Release qualification verifies that mapping from the artifact.
- Required third-party license texts, notices, modification notices, source locations,
  and relink/rebuild materials ship in the artifact or an accompanying durable bundle
  as their terms require. A URL to a moving branch is not source correspondence.

## Dependency policy

Every production, development, build, test, container, action, font, asset, parser,
and generated-code dependency is inventoried. Transitive dependencies are in scope.

### Normally allowed after automated verification

- The current normal code-dependency allowlist is limited to these exact SPDX license
  identifiers: `Apache-2.0`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`, `ISC`, and `Zlib`.
  Adding another OSI-approved license starts in manual review and requires an explicit
  allowlist policy change; a custom or non-OSI term is never inferred to be equivalent.
- Public-domain/CC0 material with verifiable provenance.
- `Unicode-3.0` data where its notice and use requirements are preserved.

“Allowed” is not automatic approval: maintenance, security, accessibility, bundle
or image cost, data collection, and paid-feature coupling still require review.

### Manual legal/architecture review required

- MPL, EPL, CDDL, LGPL, GPL, AGPL, licenses with exceptions, or any license whose
  compatibility depends on dynamic linking, file boundaries, process isolation,
  distribution method, or generated output.
- Creative Commons content other than CC0, Open Font License assets, icon packs,
  model weights, datasets, specifications with extraction restrictions, and media.
- Dual/multi-licensed projects; the exact selected option must be pinned and recorded.
- Vendored source, patched forks, abandoned packages, or dependencies with unclear
  artifact/source correspondence.
- SaaS SDKs or optional adapters whose open-source client masks a hosted-only core.

### Denied for required open-source core functionality

- Proprietary, evaluation, trial, field-of-use, user-count, revenue, noncommercial,
  no-derivatives, or “source available” terms that are not OSI-approved.
- Business Source License, Commons Clause, SSPL, Elastic License 2.0, PolyForm,
  and similar restricted terms in Odoc release artifacts or required functionality.
  Owner approval or process isolation cannot cure incompatible or prohibited terms.
  A separately obtained, separately distributed, optional adapter may be documented
  only after qualified legal review confirms that its use and distribution are lawful,
  isolated from Odoc's combined work, and unnecessary for every claimed core capability.
- Unlicensed code/assets, copied snippets without provenance, or packages whose
  declared license cannot be reconciled with their repository/artifact contents.
- A dependency that makes a roadmap-required capability available only through a
  paid extension, hosted service, or source-unavailable component.

## Adoption checklist

Before merging a new dependency, record in the change:

1. exact package, version, registry/source, checksum/lockfile, and purpose;
2. direct and transitive license scan plus manual confirmation of the upstream file;
3. maintenance/release/security history and an exit/replacement strategy;
4. known vulnerabilities and why the selected version is acceptable;
5. backend image, frontend bundle, runtime permission/network, accessibility, and
   privacy impact as applicable;
6. whether the capability works without a premium or hosted-only add-on;
7. generated/vendored content and required notices;
8. reviewer and any exception expiry.

The responsible-area maintainer owns the inventory; a release maintainer verifies the
artifact-to-source/notices bundle. Any manual-review license or exception also requires
a qualified license reviewer with no vendor conflict. `P0-002` assigns these roles
before dependencies are adopted; until then, no exception is approved.

CI introduced by `P0-011` must fail on unknown, missing, denied, or changed licenses;
it must emit reviewable backend/frontend/container SBOM and notices artifacts. A scan
is evidence, not legal analysis.

## Exceptions and policy changes

An exception identifies the package and exact version, scope, reason, alternatives,
license/security analysis, owner, review date, expiry, replacement/removal plan, and
whether release artifacts are affected. Expired exceptions fail the release gate.

Changing the Odoc project license, adding a CLA/dual-license, or weakening the
required-core rule requires public rationale, maintainer consensus under governance,
explicit project-owner approval, contributor-rights analysis, and a migration plan.
