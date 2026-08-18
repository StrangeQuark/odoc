#!/usr/bin/env bash
# Isolated smoke for the local TLS + read-only-root application path.
# It never touches the ordinary `odoc-local` Compose project.
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly local_dir="$(cd -- "${script_dir}/.." && pwd)"
readonly repository_dir="$(cd -- "${local_dir}/../.." && pwd)"
readonly project_name="${ODOC_SECURE_SMOKE_PROJECT:-odoc-secure-smoke}"
readonly docker_context="${ODOC_DOCKER_CONTEXT:-default}"
readonly include_thin_slice="${ODOC_SECURE_SMOKE_THIN_SLICE:-false}"
readonly include_two_apis="${ODOC_SECURE_SMOKE_TWO_APIS:-false}"
readonly build_images="${ODOC_SECURE_SMOKE_BUILD:-true}"
readonly include_packet_capture="${ODOC_SECURE_SMOKE_PACKET_CAPTURE:-false}"
readonly include_large_transfer="${ODOC_SECURE_SMOKE_LARGE_TRANSFER:-true}"
wrong_ca_file=""
wrong_ca_key_file=""
transport_capture_dir=""
host_capture_container_id=""
proxy_capture_container_id=""
large_upload_file=""
large_download_file=""
readonly transport_capture_image="corfr/tcpdump@sha256:3006b3bd9f041bf73f21e626b97cca5e78fd6ce271549ca95b8e6a508165512b"

: "${ODOC_POSTGRES_PORT:=25432}"
: "${ODOC_MINIO_PORT:=29000}"
: "${ODOC_MINIO_CONSOLE_PORT:=29001}"
: "${ODOC_MAILPIT_SMTP_PORT:=21025}"
: "${ODOC_MAILPIT_UI_PORT:=28025}"
: "${ODOC_API_PORT:=28080}"
: "${ODOC_HTTPS_FRONTEND_PORT:=28443}"
export ODOC_POSTGRES_PORT ODOC_MINIO_PORT ODOC_MINIO_CONSOLE_PORT ODOC_MAILPIT_SMTP_PORT
export ODOC_MAILPIT_UI_PORT ODOC_API_PORT ODOC_HTTPS_FRONTEND_PORT

compose_files=(
  -f "${local_dir}/compose.yml"
  -f "${local_dir}/compose.app.yml"
  -f "${local_dir}/compose.tls.yml"
  -f "${local_dir}/compose.hardened.yml"
)
if [[ "${include_thin_slice}" == "true" ]]; then
  compose_files+=(-f "${local_dir}/compose.thin-slice.yml")
fi
if [[ "${include_two_apis}" == "true" ]]; then
  compose_files+=(-f "${local_dir}/compose.two-api.yml")
fi

compose() {
  docker --context "${docker_context}" compose --project-name "${project_name}" \
    --env-file "${local_dir}/.env.example" \
    "${compose_files[@]}" "$@"
}

cleanup() {
  [[ -z "${host_capture_container_id}" ]] || docker --context "${docker_context}" rm --force "${host_capture_container_id}" >/dev/null 2>&1 || true
  [[ -z "${proxy_capture_container_id}" ]] || docker --context "${docker_context}" rm --force "${proxy_capture_container_id}" >/dev/null 2>&1 || true
  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  [[ -z "${wrong_ca_file}" ]] || rm -f "${wrong_ca_file}" "${wrong_ca_key_file}"
  [[ -z "${transport_capture_dir}" ]] || rm -rf -- "${transport_capture_dir}" >/dev/null 2>&1 || true
  [[ -z "${large_upload_file}" ]] || rm -f -- "${large_upload_file}" "${large_download_file}"
}
trap cleanup EXIT

report_failure() {
  local exit_code="$?"
  echo "Secure Compose smoke failed at line ${BASH_LINENO[0]} while running: ${BASH_COMMAND}. Current isolated-service status follows:" >&2
  compose ps >&2 || true
  compose logs --no-color --tail 120 api frontend worker postgres >&2 || true
  exit "${exit_code}"
}
trap report_failure ERR

"${script_dir}/bootstrap-pki.sh" >/dev/null
compose_up=(up -d)
if [[ "${build_images}" == "true" ]]; then
  compose_up+=(--build)
elif [[ "${build_images}" == "false" ]]; then
  compose_up+=(--no-build)
else
  echo 'ODOC_SECURE_SMOKE_BUILD must be true or false.' >&2
  exit 1
fi
if [[ "${include_thin_slice}" == "true" && "${include_two_apis}" == "true" ]]; then
  echo 'The no-database thin slice deliberately keeps its test-only idempotency state in process; run its command replay proof with one API replica. The two-replica stateless-route proof belongs to the full P0-013 stack.' >&2
  exit 1
fi
compose_services=(api frontend minio minio-init mailpit)
if [[ "${include_thin_slice}" != "true" ]]; then
  compose_services+=(worker)
fi
if [[ "${include_two_apis}" == "true" ]]; then
  compose_up+=(--scale api=2 "${compose_services[@]}")
else
  compose_up+=("${compose_services[@]}")
fi
compose "${compose_up[@]}"

readonly ca_file="${local_dir}/state/pki/client/ca.crt"
for attempt in $(seq 1 45); do
  if curl --fail --silent --cacert "${ca_file}" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/healthz" >/dev/null; then
    break
  fi
  sleep 1
done

curl --fail --silent --show-error --cacert "${ca_file}" \
  "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/healthz" | grep -qx 'ok'

# Negative certificate checks are just as important as the happy path: the local
# overlay must not quietly accept an untrusted CA, a hostname mismatch, or a
# certificate after its bounded validity window has elapsed.
wrong_ca_file="$(mktemp)"
wrong_ca_key_file="${wrong_ca_file}.key"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -keyout "${wrong_ca_key_file}" -out "${wrong_ca_file}" \
  -subj '/CN=Odoc wrong local CA' >/dev/null 2>&1
if curl --fail --silent --show-error --noproxy '*' --cacert "${wrong_ca_file}" \
  "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/healthz" >/dev/null 2>&1; then
  echo 'TLS smoke unexpectedly accepted an untrusted CA.' >&2
  exit 1
fi
if curl --fail --silent --show-error --noproxy '*' --cacert "${ca_file}" \
  --resolve "not-frontend.local:${ODOC_HTTPS_FRONTEND_PORT}:127.0.0.1" \
  "https://not-frontend.local:${ODOC_HTTPS_FRONTEND_PORT}/healthz" >/dev/null 2>&1; then
  echo 'TLS smoke unexpectedly accepted a mismatched frontend hostname.' >&2
  exit 1
fi
if openssl verify -attime "$(( $(date +%s) + 90 * 24 * 60 * 60 ))" \
  -CAfile "${ca_file}" "${local_dir}/state/pki/frontend/frontend.crt" >/dev/null 2>&1; then
  echo 'TLS smoke unexpectedly accepted the local certificate after expiry.' >&2
  exit 1
fi
runtime_config="$(curl --fail --silent --show-error --cacert "${ca_file}" \
  "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/runtime-config.json")"
if ! jq --exit-status '.apiBasePath == "/api/v1"' >/dev/null <<<"${runtime_config}"; then
  echo "Unexpected runtime configuration payload: ${runtime_config}" >&2
  exit 1
fi

# P0's local composition includes the TLS-backed object-store and mail services,
# not merely their image definitions. Their API adapters are deliberately later
# work, but a fresh secure stack must prove each dependency starts on the
# declared CA-pinned endpoint and that bucket initialization completes.
for attempt in $(seq 1 45); do
  if curl --fail --silent --cacert "${ca_file}" \
    "https://localhost:${ODOC_MINIO_PORT}/minio/health/live" >/dev/null \
    && curl --fail --silent --cacert "${ca_file}" \
      "https://localhost:${ODOC_MAILPIT_UI_PORT}/readyz" >/dev/null; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error --cacert "${ca_file}" \
  "https://localhost:${ODOC_MINIO_PORT}/minio/health/live" >/dev/null
curl --fail --silent --show-error --cacert "${ca_file}" \
  "https://localhost:${ODOC_MAILPIT_UI_PORT}/readyz" >/dev/null
readonly minio_init_container_id="$(compose ps -aq minio-init)"
test -n "${minio_init_container_id}"
for attempt in $(seq 1 30); do
  if [[ "$(docker --context "${docker_context}" inspect --format '{{.State.Status}} {{.State.ExitCode}}' "${minio_init_container_id}")" == 'exited 0' ]]; then
    break
  fi
  sleep 1
done
[[ "$(docker --context "${docker_context}" inspect --format '{{.State.Status}} {{.State.ExitCode}}' "${minio_init_container_id}")" == 'exited 0' ]]

# The static frontend can become healthy before Spring has finished Flyway/JPA initialization.
for attempt in $(seq 1 45); do
  if curl --fail --silent --cacert "${ca_file}" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" \
    | grep -q '"status":"ok"'; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error --cacert "${ca_file}" \
  "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" | grep -q '"status":"ok"'
if [[ "${include_two_apis}" != "true" ]]; then
  curl --fail --silent --show-error --cacert "${ca_file}" \
    "https://localhost:${ODOC_API_PORT}/actuator/health/liveness" | grep -q '"status":"UP"'
fi

# The thin slice is deliberately a no-database application.  Keep PostgreSQL
# running only to prove the normal local dependency topology still composes,
# then prove the API/worker have not caused Flyway or a domain schema to touch
# it.  This is stronger than merely filtering product endpoints from OpenAPI.
if [[ "${include_thin_slice}" == "true" ]]; then
  postgres_container_id="$(compose ps -q postgres)"
  test -n "${postgres_container_id}"
  public_table_count="$(docker --context "${docker_context}" exec "${postgres_container_id}" sh -ec \
    'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from information_schema.tables where table_schema = '\''public'\''"')"
  test "${public_table_count}" = '0'
fi

# Prove browser-to-proxy and proxy-to-API hops do not expose a classified canary
# or Basic credentials. The capture helper is digest pinned, ephemeral,
# read-only, and receives only CAP_NET_RAW; it shares the frontend network
# namespace to observe the real upstream TLS hop.
if [[ "${include_packet_capture}" == "true" ]]; then
frontend_container_id="$(compose ps -q frontend)"
test -n "${frontend_container_id}"
transport_capture_dir="$(mktemp -d)"
# Rootless Docker maps the capture helper's root UID to an unprivileged host UID.
# Let that UID write only into this random, short-lived directory without allowing it
# to list/read the capture; the owning test user can still inspect and remove it.
chmod 0733 "${transport_capture_dir}"
readonly transport_canary="odoc-transport-canary-$(date +%s)-${RANDOM}"
# tcpdump needs the effective capability after exec. Run this isolated helper as its
# default UID, but retain a read-only root, no-new-privileges, and only NET_RAW/NET_ADMIN.
host_capture_container_id="$(docker --context "${docker_context}" run --detach --network host --read-only --tmpfs /tmp --cap-drop ALL --cap-add NET_RAW --cap-add NET_ADMIN --security-opt no-new-privileges --volume "${transport_capture_dir}:/capture" "${transport_capture_image}" -i lo -U -w /capture/browser-to-proxy.pcap "tcp port ${ODOC_HTTPS_FRONTEND_PORT}")"
proxy_capture_container_id="$(docker --context "${docker_context}" run --detach --network "container:${frontend_container_id}" --read-only --tmpfs /tmp --cap-drop ALL --cap-add NET_RAW --cap-add NET_ADMIN --security-opt no-new-privileges --volume "${transport_capture_dir}:/capture" "${transport_capture_image}" -i any -U -w /capture/proxy-to-api.pcap "tcp port 8080")"
sleep 1
for capture_container_id in "${host_capture_container_id}" "${proxy_capture_container_id}"; do
  if [[ "$(docker --context "${docker_context}" inspect --format '{{.State.Running}}' "${capture_container_id}")" != "true" ]]; then
    docker --context "${docker_context}" logs "${capture_container_id}" >&2 || true
    exit 1
  fi
done
curl --fail --silent --show-error --cacert "${ca_file}" --user developer:developer -H "X-Odoc-Transport-Canary: ${transport_canary}" "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" | grep -q '"status":"ok"'
sleep 1
docker --context "${docker_context}" kill --signal INT "${host_capture_container_id}" >/dev/null
docker --context "${docker_context}" kill --signal INT "${proxy_capture_container_id}" >/dev/null
docker --context "${docker_context}" wait "${host_capture_container_id}" "${proxy_capture_container_id}" >/dev/null
docker --context "${docker_context}" rm "${host_capture_container_id}" "${proxy_capture_container_id}" >/dev/null
host_capture_container_id=""
proxy_capture_container_id=""
test -s "${transport_capture_dir}/browser-to-proxy.pcap"
test -s "${transport_capture_dir}/proxy-to-api.pcap"
if strings "${transport_capture_dir}/browser-to-proxy.pcap" "${transport_capture_dir}/proxy-to-api.pcap" | grep -F -e "${transport_canary}" -e 'developer:developer' >/dev/null; then
  echo 'TLS packet capture exposed a canary or Basic credential in plaintext.' >&2
  exit 1
fi
fi

# Rotate only the API leaf certificate under the same local CA. The proxy must
# continue hostname/CA verification after the API restarts; image rebuilding is
# neither necessary nor acceptable for a routine certificate rotation.
readonly api_pki_dir="${local_dir}/state/pki/api"
readonly api_cert_before="$(openssl x509 -in "${api_pki_dir}/api.crt" -noout -serial)"
readonly api_rotation_dir="$(mktemp -d)"
api_rotation_cleanup() { rm -rf -- "${api_rotation_dir}" >/dev/null 2>&1 || true; }
trap 'api_rotation_cleanup; cleanup' EXIT
openssl req -new -nodes -newkey rsa:3072 \
  -keyout "${api_rotation_dir}/api.key" -out "${api_rotation_dir}/api.csr" \
  -subj '/CN=api' >/dev/null 2>&1
cat >"${api_rotation_dir}/api.ext" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:api,DNS:localhost,IP:127.0.0.1
EOF
openssl x509 -req -in "${api_rotation_dir}/api.csr" \
  -CA "${local_dir}/state/pki/ca/ca.crt" \
  -CAkey "${local_dir}/state/pki/ca/ca.key" \
  -CAcreateserial -out "${api_rotation_dir}/api.crt" -days 30 -sha256 \
  -extfile "${api_rotation_dir}/api.ext" >/dev/null 2>&1
openssl pkcs12 -export -out "${api_rotation_dir}/api.p12" \
  -inkey "${api_rotation_dir}/api.key" -in "${api_rotation_dir}/api.crt" \
  -certfile "${local_dir}/state/pki/ca/ca.crt" -name odoc-api \
  -passout pass:odoc-local-only >/dev/null 2>&1
install -m 0644 "${api_rotation_dir}/api.p12" "${api_pki_dir}/api.p12"
install -m 0644 "${api_rotation_dir}/api.crt" "${api_pki_dir}/api.crt"
install -m 0600 "${api_rotation_dir}/api.key" "${api_pki_dir}/api.key"
readonly api_cert_after="$(openssl x509 -in "${api_pki_dir}/api.crt" -noout -serial)"
test "${api_cert_before}" != "${api_cert_after}"
compose restart api >/dev/null
for attempt in $(seq 1 30); do
  if curl --fail --silent --cacert "${ca_file}" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" \
    | grep -q '"status":"ok"'; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error --cacert "${ca_file}" \
  "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" | grep -q '"status":"ok"'

if [[ "${include_thin_slice}" == "true" ]]; then
  readonly idempotency_key="phase0-smoke-key"
  first_response="$(curl --fail --silent --show-error --cacert "${ca_file}" \
    --user developer:developer \
    -H "Idempotency-Key: ${idempotency_key}" \
    -H 'Content-Type: application/json' \
    --data '{"message":"secure thin slice"}' \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/test/commands/echo")"
  replay_response="$(curl --fail --silent --show-error --cacert "${ca_file}" \
    --user developer:developer \
    -H "Idempotency-Key: ${idempotency_key}" \
    -H 'Content-Type: application/json' \
    --data '{"message":"secure thin slice"}' \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/test/commands/echo")"
  test "${first_response}" = "${replay_response}"
fi

# Exercise the real multipart path with a bounded multi-megabyte canary, then
# interrupt a throttled retry by killing the API container. The development
# media implementation remains intentionally small and database-backed; this
# checks the Phase-0 transport invariant only: Nginx and Spring writable paths
# must not retain the classified payload after a normal transfer or a crash.
if [[ "${include_large_transfer}" == "true" && "${include_thin_slice}" != "true" ]]; then
  readonly large_transfer_canary="odoc-large-transfer-canary-$(date +%s)-${RANDOM}"
  large_upload_file="$(mktemp)"
  large_download_file="$(mktemp)"
  printf '\377\330\377\340%s\n' "${large_transfer_canary}" >"${large_upload_file}"
  dd if=/dev/zero bs=1M count=8 status=none >>"${large_upload_file}"
  readonly smoke_space_key="secure-smoke-${RANDOM}"
  readonly smoke_space_id="$(curl --fail --silent --show-error --cacert "${ca_file}" \
    --user developer:developer -H 'Content-Type: application/json' \
    --data "{\"key\":\"${smoke_space_key}\",\"name\":\"Secure smoke media\",\"description\":\"transport test\"}" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/spaces" | jq -r '.id')"
  test "${smoke_space_id}" != 'null'
  readonly uploaded_media_id="$(curl --fail --silent --show-error --cacert "${ca_file}" \
    --user developer:developer -F "file=@${large_upload_file};type=image/jpeg" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/spaces/${smoke_space_id}/media" | jq -r '.id')"
  test "${uploaded_media_id}" != 'null'
  curl --fail --silent --show-error --cacert "${ca_file}" --user developer:developer \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/media/${uploaded_media_id}" >"${large_download_file}"
  grep -Fq -- "${large_transfer_canary}" "${large_download_file}"

  api_container_id_for_crash="$(compose ps -q api | head -n 1)"
  test -n "${api_container_id_for_crash}"
  curl --silent --show-error --cacert "${ca_file}" --user developer:developer \
    --limit-rate 256k -F "file=@${large_upload_file};type=image/jpeg" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/spaces/${smoke_space_id}/media" >/dev/null 2>&1 &
  readonly interrupted_upload_pid="$!"
  sleep 1
  docker --context "${docker_context}" kill "${api_container_id_for_crash}" >/dev/null
  wait "${interrupted_upload_pid}" >/dev/null 2>&1 || true
  compose up -d api >/dev/null
  for attempt in $(seq 1 45); do
    if curl --fail --silent --cacert "${ca_file}" \
      "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" | grep -q '"status":"ok"'; then
      break
    fi
    sleep 1
  done
  curl --fail --silent --show-error --cacert "${ca_file}" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" | grep -q '"status":"ok"'
  for service_name in api frontend; do
    service_container_id="$(compose ps -q "${service_name}" | head -n 1)"
    test -n "${service_container_id}"
    if docker --context "${docker_context}" exec "${service_container_id}" sh -ec \
      "grep -R -a -F -- '${large_transfer_canary}' /tmp /var/cache/nginx 2>/dev/null"; then
      echo 'Large-transfer canary remained in an application or proxy writable path.' >&2
      exit 1
    fi
  done
fi

mapfile -t api_container_ids < <(compose ps -q api)
test "${#api_container_ids[@]}" -ge 1
if [[ "${include_two_apis}" == "true" ]]; then
  test "${#api_container_ids[@]}" -eq 2
fi
for container_id in "${api_container_ids[@]}"; do
  test -n "${container_id}"
  docker --context "${docker_context}" inspect "${container_id}" \
    --format '{{.HostConfig.ReadonlyRootfs}} {{json .HostConfig.CapDrop}}' \
    | grep -Fqx 'true ["ALL"]'
done
frontend_container_id="$(compose ps -q frontend)"
test -n "${frontend_container_id}"
docker --context "${docker_context}" inspect "${frontend_container_id}" \
  --format '{{.HostConfig.ReadonlyRootfs}} {{json .HostConfig.CapDrop}}' \
  | grep -Fqx 'true ["ALL"]'

# Runtime layers contain exactly the application/runtime files needed by each
# image; source worktrees, VCS metadata, and build caches never cross the
# multi-stage boundary. Execute the checks inside the running immutable images
# rather than relying only on Dockerfile review.
api_container_id="${api_container_ids[0]}"
docker --context "${docker_context}" exec "${api_container_id}" sh -ec \
  'test ! -e /workspace && test ! -e /root/.m2 && test ! -e /app/.git'
docker --context "${docker_context}" exec "${frontend_container_id}" sh -ec \
  'test ! -e /workspace && test ! -e /root/.cache && test ! -e /usr/share/nginx/html/.git'

if [[ "${include_two_apis}" == "true" ]]; then
  # System info is deliberately non-stateful. Stop one replica and verify the
  # same-origin frontend proxy resolves and serves the surviving replica.
  docker --context "${docker_context}" stop --time 10 "${api_container_id}" >/dev/null
  api_container_id="${api_container_ids[1]}"
  for attempt in $(seq 1 15); do
    if curl --fail --silent --cacert "${ca_file}" \
      "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" \
      | grep -q '"status":"ok"'; then
      break
    fi
    sleep 1
  done
  curl --fail --silent --show-error --cacert "${ca_file}" \
    "https://localhost:${ODOC_HTTPS_FRONTEND_PORT}/api/v1/system/info" \
    | grep -q '"status":"ok"'
fi

# Docker sends the production stop signal to PID 1. The application must exit
# cleanly inside the documented bound, rather than relying on a forced kill.
shutdown_started="$(date +%s)"
docker --context "${docker_context}" stop --time 10 "${api_container_id}" >/dev/null
shutdown_elapsed="$(( $(date +%s) - shutdown_started ))"
test "${shutdown_elapsed}" -le 10
api_exit_code="$(docker --context "${docker_context}" inspect "${api_container_id}" \
  --format '{{.State.ExitCode}}')"
# The JVM may report either a clean zero exit or the conventional 128+SIGTERM
# status after Spring has drained. A timeout would instead be a later SIGKILL
# status and fails this narrow allowlist.
[[ "${api_exit_code}" == '0' || "${api_exit_code}" == '143' ]]

echo "Secure Compose smoke passed for project ${project_name}."
