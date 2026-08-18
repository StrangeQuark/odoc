#!/usr/bin/env bash
# Generates development-only certificates for the opt-in local TLS Compose overlay.
# Private keys stay under deploy/local/state/, which is ignored by Git.
set -euo pipefail

readonly script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly local_dir="$(cd -- "${script_dir}/.." && pwd)"
readonly state_dir="${ODOC_LOCAL_PKI_DIR:-${local_dir}/state/pki}"
readonly validity_days="${ODOC_LOCAL_PKI_VALIDITY_DAYS:-30}"

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required to create the local development PKI." >&2
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is required to create the local Java trust store." >&2
  exit 1
fi

if ! [[ "${validity_days}" =~ ^[1-9][0-9]*$ ]]; then
  echo "ODOC_LOCAL_PKI_VALIDITY_DAYS must be a positive integer." >&2
  exit 1
fi

umask 077
mkdir -p "${state_dir}"/{ca,postgres,minio/CAs,mailpit,client,api,frontend}

readonly ca_key="${state_dir}/ca/ca.key"
readonly ca_cert="${state_dir}/ca/ca.crt"

if [[ ! -s "${ca_key}" || ! -s "${ca_cert}" ]]; then
  openssl req -x509 -new -nodes -newkey rsa:3072 \
    -keyout "${ca_key}" -out "${ca_cert}" -days "${validity_days}" \
    -subj "/CN=Odoc local development CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"
fi

issue_server_certificate() {
  local name="$1"
  local common_name="$2"
  local subject_alt_names="$3"
  local destination="$4"
  local key="${destination}/${name}.key"
  local csr="${destination}/${name}.csr"
  local certificate="${destination}/${name}.crt"
  local extensions="${destination}/${name}.ext"

  if [[ -s "${key}" && -s "${certificate}" ]]; then
    return
  fi

  openssl req -new -nodes -newkey rsa:3072 -keyout "${key}" -out "${csr}" \
    -subj "/CN=${common_name}"
  cat >"${extensions}" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=${subject_alt_names}
EOF
  openssl x509 -req -in "${csr}" -CA "${ca_cert}" -CAkey "${ca_key}" -CAcreateserial \
    -out "${certificate}" -days "${validity_days}" -sha256 -extfile "${extensions}"
  rm -f "${csr}" "${extensions}"
}

issue_server_certificate postgres postgres "DNS:postgres,DNS:localhost,IP:127.0.0.1" "${state_dir}/postgres"
issue_server_certificate minio minio "DNS:minio,DNS:localhost,IP:127.0.0.1" "${state_dir}/minio"
issue_server_certificate mailpit mailpit "DNS:mailpit,DNS:localhost,IP:127.0.0.1" "${state_dir}/mailpit"
issue_server_certificate api api "DNS:api,DNS:localhost,IP:127.0.0.1" "${state_dir}/api"
issue_server_certificate frontend frontend "DNS:frontend,DNS:localhost,IP:127.0.0.1" "${state_dir}/frontend"

if [[ ! -s "${state_dir}/api/api.p12" ]]; then
  openssl pkcs12 -export -out "${state_dir}/api/api.p12" \
    -inkey "${state_dir}/api/api.key" -in "${state_dir}/api/api.crt" \
    -certfile "${ca_cert}" -name odoc-api -passout pass:odoc-local-only
fi

cp "${state_dir}/minio/minio.crt" "${state_dir}/minio/public.crt"
cp "${state_dir}/minio/minio.key" "${state_dir}/minio/private.key"
cp "${ca_cert}" "${state_dir}/minio/CAs/ca.crt"
cp "${ca_cert}" "${state_dir}/postgres/ca.crt"
cp "${ca_cert}" "${state_dir}/client/ca.crt"

if [[ ! -s "${state_dir}/client/truststore.p12" ]]; then
  keytool -importcert -noprompt -alias odoc-local-ca -file "${ca_cert}" \
    -keystore "${state_dir}/client/truststore.p12" -storetype PKCS12 -storepass odoc-local-only
fi

cat >"${state_dir}/postgres/pg_hba.conf" <<'EOF'
# Generated for Odoc local TLS development.  Plain TCP is rejected deliberately.
local   all             all                                     trust
hostnossl all           all             0.0.0.0/0               reject
hostnossl all           all             ::0/0                   reject
hostssl all             all             0.0.0.0/0               scram-sha-256
hostssl all             all             ::0/0                   scram-sha-256
EOF

find "${state_dir}" -type f -name '*.key' -exec chmod 0600 {} +
find "${state_dir}" -type f -name '*.crt' -exec chmod 0644 {} +
chmod 0644 "${state_dir}/postgres/pg_hba.conf"
# The CA is deliberately non-secret and must be readable by the non-root API/worker image.
# Keep the CA branch traversable without making any private-key directory listable/readable.
chmod 0711 "${local_dir}/state" "${state_dir}"
chmod 0755 "${state_dir}/client"
# The API image runs as UID 10001 and needs only a read-only, local-development PKCS#12
# bundle. Its source directory is traversable but never committed; production uses a secret
# manager and a separate deployment-specific key delivery mechanism.
chmod 0755 "${state_dir}/api" "${state_dir}/frontend"
chmod 0644 "${state_dir}/api/api.p12" "${state_dir}/frontend/frontend.crt"
chmod 0644 "${state_dir}/client/truststore.p12"
# Bind mounts preserve host ownership. The unprivileged local Nginx image therefore needs a
# readable copy of its *development-only* key; the ignored state directory must stay local.
chmod 0644 "${state_dir}/frontend/frontend.key"

echo "Local PKI ready in ${state_dir} (valid for ${validity_days} days)."
echo "Use it only with deploy/local/compose.tls.yml; never copy these keys to production."
