#!/usr/bin/env bash
# Prove the two backend CI gates below fail closed without retaining a malformed
# license or secret-shaped value in the worktree or Git history.
set -euo pipefail

readonly gitleaks_image="${GITLEAKS_IMAGE:-ghcr.io/gitleaks/gitleaks@sha256:691af3c7c5a48b16f187ce3446d5f194838f91238f27270ed36eef6359a574d9}"
readonly fixture_dir="$(mktemp -d)"

cleanup() {
  rm -rf -- "${fixture_dir}"
}
trap cleanup EXIT

for command_name in docker grep; do
  command -v "${command_name}" >/dev/null || {
    echo "Required command is unavailable: ${command_name}" >&2
    exit 1
  }
done

printf 'not the Apache License\n' >"${fixture_dir}/LICENSE"
if grep -Fqx '                                 Apache License' "${fixture_dir}/LICENSE"; then
  echo 'The intentionally malformed license unexpectedly passed the Apache gate.' >&2
  exit 1
fi

cat >"${fixture_dir}/.gitleaks.toml" <<'EOF'
[[rules]]
id = "odoc-ci-negative-secret"
description = "Synthetic CI-only secret detector control"
regex = '''odoc-ci-negative-secret-[A-Za-z0-9]{24}'''
EOF
printf 'token = "odoc-ci-negative-secret-0123456789abcdefABCDEF12"\n' >"${fixture_dir}/fixture.txt"
if docker run --rm --network none \
  -v "${fixture_dir}:/scan:ro" \
  "${gitleaks_image}" detect --no-git --source /scan --config /scan/.gitleaks.toml >/dev/null 2>&1; then
  echo 'The intentionally secret-shaped fixture unexpectedly passed Gitleaks.' >&2
  exit 1
fi

echo 'Backend negative CI controls passed: malformed license and synthetic secret were rejected.'
