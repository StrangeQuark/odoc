# CI and branch-protection baseline

**Owner:** P0-011  
**Scope:** `odoc` and `odoc-react` default development branches

The repository workflows are intentionally runnable without production credentials.
They validate source, generated contracts, test fixtures, production images, and
dependency/secret scanning only. Deployment, external GitHub credentials, and KMS
credentials are not CI inputs.

## Required pull-request checks

Enable these checks for `develop` and `main` after the first CI run has published
their exact GitHub check names:

- backend: `Backend CI / verify`, `Backend CI / secret-scan`, and
  `Backend CI / secure-compose-smoke`;
- frontend: `Frontend CI / verify` and `Frontend CI / secret-scan`.

Require the branch to be up to date before merge, block force pushes and deletion,
and require conversation resolution. Require one qualified reviewer for ordinary
changes; apply the stricter review and governance rules in `GOVERNANCE.md` to
security, cryptography, public API, schema migration, and license changes. Do not
allow an administrator bypass for release qualification without a recorded,
time-bounded exception under the project governance policy.

GitHub settings cannot be changed safely from the working tree. The maintainer must
configure them in **Repository settings → Branches / Rulesets**, then record the
rule name, protected branches, required checks, and verification date in the P0-011
ledger row before marking the package complete.

## Local-to-CI mapping

| Repository | Local command | CI gate |
| --- | --- | --- |
| `odoc` | `./mvnw --batch-mode verify` | `Backend CI / verify` |
| `odoc` | `./deploy/local/scripts/verify-secure-stack.sh` | `Backend CI / secure-compose-smoke` |
| `odoc` | `./deploy/helm/odoc/scripts/verify-tls-render.sh` | `Backend CI / secure-compose-smoke` |
| `odoc-react` | `corepack pnpm format:check && corepack pnpm lint && corepack pnpm typecheck && corepack pnpm test && corepack pnpm build` | `Frontend CI / verify` |
| `odoc-react` | `corepack pnpm api:check && corepack pnpm api:guard:proof` | `Frontend CI / verify` |

The CI artifact retention period is seven days. Artifacts must contain no production
credentials, browser profiles, private customer data, or decrypted application data.
Failure diagnostics should be retained only when necessary to debug the failed run.

## Negative controls

The check suite must retain reproducible evidence that a broken format, unit test,
OpenAPI contract, license declaration, and secret fixture fail the intended check,
then remove the fixture before any merge. Use an isolated temporary checkout or a
throwaway branch; never commit an actual secret or a secret-shaped production value.
The existing `pnpm api:guard:proof` is the retained automated contract negative
control. The remaining CI-negative-control evidence is an explicit P0-011 completion
item, not an excuse to weaken a gate.
