#!/usr/bin/env bash
# Disposable local smoke for the Phase 0 Helm chart. This script creates and
# deletes only its uniquely named kind cluster; it never uses a pre-existing
# cluster or the ordinary Docker Compose project.
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly chart_dir="$(cd -- "${script_dir}/.." && pwd)"
readonly backend_dir="$(cd -- "${chart_dir}/../../.." && pwd)"
readonly frontend_dir="${ODOC_KIND_SMOKE_FRONTEND_DIR:-$(cd -- "${backend_dir}/../odoc-react" && pwd)}"
readonly cluster_name="${ODOC_KIND_SMOKE_CLUSTER:-odoc-kind-smoke}"
readonly namespace="${ODOC_KIND_SMOKE_NAMESPACE:-odoc-smoke}"
readonly release_name="${ODOC_KIND_SMOKE_RELEASE:-odoc}"
readonly local_port="${ODOC_KIND_SMOKE_PORT:-18081}"
readonly local_api_port="${ODOC_KIND_SMOKE_API_PORT:-18082}"
readonly include_tls="${ODOC_KIND_SMOKE_TLS:-false}"
readonly image_tag="kind-smoke-$(date +%s)"
port_forward_pid=""
api_port_forward_pid=""
tls_dir=""

require_command() {
  command -v "$1" >/dev/null || {
    echo "Required command is unavailable: $1" >&2
    exit 1
  }
}

cleanup() {
  if [[ -n "${port_forward_pid}" ]]; then
    kill -TERM "${port_forward_pid}" >/dev/null 2>&1 || true
    wait "${port_forward_pid}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${api_port_forward_pid}" ]]; then
    kill -TERM "${api_port_forward_pid}" >/dev/null 2>&1 || true
    wait "${api_port_forward_pid}" >/dev/null 2>&1 || true
  fi
  helm uninstall "${release_name}" --namespace "${namespace}" --wait >/dev/null 2>&1 || true
  kind delete cluster --name "${cluster_name}" >/dev/null 2>&1 || true
  [[ -z "${tls_dir}" ]] || rm -rf -- "${tls_dir}"
}
trap cleanup EXIT

for command_name in docker helm kind kubectl curl openssl; do
  require_command "${command_name}"
done

if kind get clusters | grep -Fxq "${cluster_name}"; then
  echo "Refusing to reuse existing kind cluster ${cluster_name}. Choose a new ODOC_KIND_SMOKE_CLUSTER or delete it yourself." >&2
  exit 1
fi
if [[ ! -d "${frontend_dir}" ]]; then
  echo "Expected sibling frontend checkout at ${frontend_dir}." >&2
  exit 1
fi

helm lint "${chart_dir}"
helm template "${release_name}" "${chart_dir}" >/dev/null

docker build --tag "odoc-api:${image_tag}" "${backend_dir}"
docker build --tag "odoc-frontend:${image_tag}" "${frontend_dir}"
kind create cluster --name "${cluster_name}" --wait 90s
kind load docker-image --name "${cluster_name}" "odoc-api:${image_tag}" "odoc-frontend:${image_tag}"

kubectl create namespace "${namespace}"
readonly database_password="$(openssl rand -hex 24)"
kubectl --namespace "${namespace}" create secret generic odoc-database \
  --from-literal=username=odoc \
  --from-literal=password="${database_password}"
if [[ "${include_tls}" == "true" ]]; then
  tls_dir="$(mktemp -d)"
  openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
    -keyout "${tls_dir}/ca.key" -out "${tls_dir}/ca.crt" -subj '/CN=odoc-kind-smoke-ca' >/dev/null 2>&1
  for service_name in postgres odoc-api; do
    openssl req -new -newkey rsa:2048 -nodes -keyout "${tls_dir}/${service_name}.key" \
      -out "${tls_dir}/${service_name}.csr" -subj "/CN=${service_name}" >/dev/null 2>&1
    printf 'subjectAltName=DNS:%s\nextendedKeyUsage=serverAuth\n' "${service_name}" >"${tls_dir}/${service_name}.ext"
    openssl x509 -req -in "${tls_dir}/${service_name}.csr" -CA "${tls_dir}/ca.crt" \
      -CAkey "${tls_dir}/ca.key" -CAcreateserial -days 1 -out "${tls_dir}/${service_name}.crt" \
      -extfile "${tls_dir}/${service_name}.ext" >/dev/null 2>&1
  done
  openssl pkcs12 -export -out "${tls_dir}/api.p12" -inkey "${tls_dir}/odoc-api.key" \
    -in "${tls_dir}/odoc-api.crt" -certfile "${tls_dir}/ca.crt" -name odoc-api \
    -passout pass:kind-smoke-only >/dev/null 2>&1
  kubectl --namespace "${namespace}" create configmap odoc-platform-ca --from-file=ca.crt="${tls_dir}/ca.crt"
  kubectl --namespace "${namespace}" create secret generic odoc-api-tls \
    --from-file=api.p12="${tls_dir}/api.p12" --from-literal=password=kind-smoke-only
  kubectl --namespace "${namespace}" create secret generic odoc-postgres-tls \
    --from-file=postgres.crt="${tls_dir}/postgres.crt" --from-file=postgres.key="${tls_dir}/postgres.key" \
    --from-file=ca.crt="${tls_dir}/ca.crt"
  kubectl --namespace "${namespace}" apply -f "${chart_dir}/examples/postgres-tls-dev.yaml"
else
  kubectl --namespace "${namespace}" apply -f "${chart_dir}/examples/postgres-dev.yaml"
fi
kubectl --namespace "${namespace}" rollout status deployment/postgres --timeout=120s

helm_args=(upgrade --install "${release_name}" "${chart_dir}" --namespace "${namespace}"
  --set api.image.repository=odoc-api \
  --set api.image.tag="${image_tag}" \
  --set api.image.pullPolicy=IfNotPresent \
  --set worker.image.repository=odoc-api \
  --set worker.image.tag="${image_tag}" \
  --set worker.image.pullPolicy=IfNotPresent \
  --set frontend.image.repository=odoc-frontend \
  --set frontend.image.tag="${image_tag}" \
  --set frontend.image.pullPolicy=IfNotPresent \
  --wait --timeout=180s)
if [[ "${include_tls}" == "true" ]]; then
  tls_jdbc_url='jdbc:postgresql://postgres:5432/odoc?sslmode=verify-full&sslrootcert=/tls/ca.crt'
  helm_args+=(--set-string "api.database.url=${tls_jdbc_url}" --set api.serverTls.enabled=true \
    --set api.serverTls.existingSecret=odoc-api-tls --set api.serverTls.trustBundleConfigMap=odoc-platform-ca \
    --set-string "worker.database.url=${tls_jdbc_url}" --set worker.trustBundleConfigMap=odoc-platform-ca \
    --set frontend.upstreamTls.enabled=true --set frontend.upstreamTls.trustBundleConfigMap=odoc-platform-ca)
fi
helm "${helm_args[@]}"

kubectl --namespace "${namespace}" rollout status deployment/odoc-api --timeout=30s
kubectl --namespace "${namespace}" rollout status deployment/odoc-worker --timeout=30s
kubectl --namespace "${namespace}" rollout status deployment/odoc-frontend --timeout=30s
kubectl --namespace "${namespace}" get pods -l app.kubernetes.io/instance="${release_name}"

kubectl --namespace "${namespace}" port-forward --address 127.0.0.1 \
  service/odoc-frontend "${local_port}:80" >/dev/null 2>&1 &
port_forward_pid="$!"
for attempt in $(seq 1 30); do
  if curl --fail --silent "http://127.0.0.1:${local_port}/healthz" | grep -qx 'ok'; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error "http://127.0.0.1:${local_port}/healthz" | grep -qx 'ok'
curl --fail --silent --show-error --user developer:developer \
  "http://127.0.0.1:${local_port}/api/v1/system/info" | grep -q '"status":"ok"'
curl --fail --silent --show-error "http://127.0.0.1:${local_port}/spaces/an-opaque-page-id" | grep -q 'id="root"'

if [[ "${include_tls}" == "true" ]]; then
  kubectl --namespace "${namespace}" port-forward --address 127.0.0.1 \
    service/odoc-api "${local_api_port}:8080" >/dev/null 2>&1 &
  api_port_forward_pid="$!"
  for attempt in $(seq 1 30); do
    if curl --fail --silent --cacert "${tls_dir}/ca.crt" \
      --resolve "odoc-api:${local_api_port}:127.0.0.1" \
      "https://odoc-api:${local_api_port}/api/v1/system/info" | grep -q '"status":"ok"'; then
      break
    fi
    sleep 1
  done
  curl --fail --silent --show-error --cacert "${tls_dir}/ca.crt" \
    --resolve "odoc-api:${local_api_port}:127.0.0.1" \
    "https://odoc-api:${local_api_port}/api/v1/system/info" | grep -q '"status":"ok"'
  if curl --fail --silent --cacert "${tls_dir}/ca.crt" \
    "https://127.0.0.1:${local_api_port}/api/v1/system/info" >/dev/null 2>&1; then
    echo 'TLS smoke unexpectedly accepted a wrong API hostname.' >&2
    exit 1
  fi
  if curl --fail --silent "http://127.0.0.1:${local_api_port}/api/v1/system/info" >/dev/null 2>&1; then
    echo 'TLS smoke unexpectedly accepted plaintext API traffic.' >&2
    exit 1
  fi
fi

echo "kind Helm smoke passed for disposable cluster ${cluster_name}."
