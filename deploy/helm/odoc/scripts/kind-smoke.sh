#!/usr/bin/env bash
# Disposable smoke for the lightweight optional Helm chart. It creates one
# kind cluster and removes it on exit; it never uses a caller's cluster.
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly chart_dir="$(cd -- "${script_dir}/.." && pwd)"
readonly backend_dir="$(cd -- "${chart_dir}/../../.." && pwd)"
readonly frontend_dir="${ODOC_KIND_SMOKE_FRONTEND_DIR:-$(cd -- "${backend_dir}/../odoc-react" && pwd)}"
readonly cluster_name="${ODOC_KIND_SMOKE_CLUSTER:-odoc-kind-smoke}"
readonly namespace="${ODOC_KIND_SMOKE_NAMESPACE:-odoc-smoke}"
readonly release_name="${ODOC_KIND_SMOKE_RELEASE:-odoc}"
readonly local_port="${ODOC_KIND_SMOKE_PORT:-18081}"
readonly image_tag="kind-smoke-$(date +%s)"
port_forward_pid=""

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
  helm uninstall "${release_name}" --namespace "${namespace}" --wait >/dev/null 2>&1 || true
  kind delete cluster --name "${cluster_name}" >/dev/null 2>&1 || true
  # `kind delete` can leave this labeled container behind when cluster creation
  # fails before kubeconfig exists. It is safe to remove only the exact label
  # for this disposable smoke cluster.
  docker ps -aq --filter "label=io.x-k8s.kind.cluster=${cluster_name}" | xargs -r docker rm -f >/dev/null 2>&1 || true
}
trap cleanup EXIT

for command_name in docker helm kind kubectl curl; do
  require_command "${command_name}"
done

if kind get clusters | grep -Fxq "${cluster_name}"; then
  echo "Refusing to reuse existing kind cluster ${cluster_name}. Choose a new ODOC_KIND_SMOKE_CLUSTER or delete it yourself." >&2
  exit 1
fi
[[ -d "${frontend_dir}" ]] || { echo "Expected sibling frontend checkout at ${frontend_dir}." >&2; exit 1; }

helm lint "${chart_dir}"
helm template "${release_name}" "${chart_dir}" >/dev/null

docker build --tag "odoc-api:${image_tag}" "${backend_dir}"
docker build --tag "odoc-frontend:${image_tag}" "${frontend_dir}"
kind create cluster --name "${cluster_name}" --wait 90s
kind load docker-image --name "${cluster_name}" "odoc-api:${image_tag}" "odoc-frontend:${image_tag}"

kubectl create namespace "${namespace}"
kubectl --namespace "${namespace}" create secret generic odoc-database \
  --from-literal=username=odoc --from-literal=password=odoc-kind-smoke-only
# Fixed key for this disposable local test only; never reuse it outside kind.
kubectl --namespace "${namespace}" create secret generic odoc-encryption \
  --from-literal=wrapping-key-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA
kubectl --namespace "${namespace}" apply -f "${chart_dir}/examples/postgres-dev.yaml"
kubectl --namespace "${namespace}" rollout status deployment/postgres --timeout=120s

helm upgrade --install "${release_name}" "${chart_dir}" --namespace "${namespace}" \
  --set api.image.repository=odoc-api --set api.image.tag="${image_tag}" --set api.image.pullPolicy=IfNotPresent \
  --set frontend.image.repository=odoc-frontend --set frontend.image.tag="${image_tag}" --set frontend.image.pullPolicy=IfNotPresent \
  --wait --timeout=180s

kubectl --namespace "${namespace}" rollout status deployment/odoc-api --timeout=60s
kubectl --namespace "${namespace}" rollout status deployment/odoc-frontend --timeout=60s
kubectl --namespace "${namespace}" port-forward --address 127.0.0.1 service/odoc-frontend "${local_port}:80" >/dev/null 2>&1 &
port_forward_pid="$!"
for attempt in $(seq 1 30); do
  if curl --fail --silent "http://127.0.0.1:${local_port}/healthz" | grep -qx 'ok'; then break; fi
  sleep 1
done
curl --fail --silent --show-error "http://127.0.0.1:${local_port}/healthz" | grep -qx 'ok'
curl --fail --silent --show-error --user developer:developer \
  "http://127.0.0.1:${local_port}/api/v1/system/info" | grep -q '"status":"ok"'
curl --fail --silent --show-error "http://127.0.0.1:${local_port}/spaces/an-opaque-page-id" | grep -q 'id="root"'

echo "kind Helm smoke passed for disposable cluster ${cluster_name}."
