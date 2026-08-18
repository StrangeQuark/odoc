#!/usr/bin/env bash
# Static contract proof for the optional production TLS topology. It complements
# kind-smoke.sh: kind uses an HTTP-only disposable fixture, while this script
# proves that the production values render CA-pinned API probes and an HTTPS
# frontend upstream, and that partial TLS configurations are rejected.
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly chart_dir="$(cd -- "${script_dir}/.." && pwd)"
readonly production_values="${chart_dir}/examples/production-values.example.yaml"
readonly output_file="$(mktemp)"
readonly error_file="$(mktemp)"

cleanup() {
  rm -f -- "${output_file}" "${error_file}"
}
trap cleanup EXIT

require_rendered() {
  local expected="$1"
  grep -Fq -- "${expected}" "${output_file}" || {
    echo "Expected production TLS render to contain: ${expected}" >&2
    exit 1
  }
}

helm lint "${chart_dir}" --values "${production_values}"
helm template odoc-production "${chart_dir}" --values "${production_values}" >"${output_file}"

require_rendered 'proxy_pass https://odoc-api:8080;'
require_rendered 'proxy_ssl_verify on;'
require_rendered 'curl --fail --silent --cacert /tls/ca.crt --resolve odoc-api:8080:127.0.0.1 https://odoc-api:8080/actuator/health >/dev/null'
require_rendered 'name: ODOC_SERVER_TLS_KEY_STORE'

if helm template odoc-invalid "${chart_dir}" \
  --set api.serverTls.enabled=true \
  --set frontend.upstreamTls.enabled=false >"${output_file}" 2>"${error_file}"; then
  echo "Helm accepted an API TLS configuration without frontend upstream TLS." >&2
  exit 1
fi

echo "Helm production TLS render contract passed."
