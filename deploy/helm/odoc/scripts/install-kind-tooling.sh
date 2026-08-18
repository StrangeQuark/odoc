#!/usr/bin/env bash
# Install the exact disposable-kind smoke tooling without sudo. Every downloaded
# binary is checked against its release-published SHA-256 before it is exposed on PATH.
set -euo pipefail

readonly destination="${1:?usage: install-kind-tooling.sh DESTINATION}"
readonly kind_version="v0.32.0"
readonly kubectl_version="v1.34.0"
readonly helm_version="v3.19.0"
readonly work_dir="$(mktemp -d)"

cleanup() {
  rm -rf -- "${work_dir}"
}
trap cleanup EXIT

mkdir -p "${destination}"

download_verified() {
  local artifact_url="$1"
  local checksum_url="$2"
  local destination_file="$3"
  local expected
  local actual

  curl --fail --silent --show-error --location --output "${destination_file}" "${artifact_url}"
  expected="$(curl --fail --silent --show-error --location "${checksum_url}" | awk 'NR == 1 { print $1 }')"
  actual="$(sha256sum "${destination_file}" | awk '{ print $1 }')"
  [[ -n "${expected}" && "${actual}" == "${expected}" ]] || {
    echo "SHA-256 verification failed for ${artifact_url}" >&2
    exit 1
  }
}

download_verified \
  "https://kind.sigs.k8s.io/dl/${kind_version}/kind-linux-amd64" \
  "https://kind.sigs.k8s.io/dl/${kind_version}/kind-linux-amd64.sha256sum" \
  "${destination}/kind"
chmod 0755 "${destination}/kind"

download_verified \
  "https://dl.k8s.io/release/${kubectl_version}/bin/linux/amd64/kubectl" \
  "https://dl.k8s.io/release/${kubectl_version}/bin/linux/amd64/kubectl.sha256" \
  "${destination}/kubectl"
chmod 0755 "${destination}/kubectl"

readonly helm_archive="${work_dir}/helm.tar.gz"
download_verified \
  "https://get.helm.sh/helm-${helm_version}-linux-amd64.tar.gz" \
  "https://get.helm.sh/helm-${helm_version}-linux-amd64.tar.gz.sha256sum" \
  "${helm_archive}"
tar --extract --gzip --file "${helm_archive}" --directory "${work_dir}"
install -m 0755 "${work_dir}/linux-amd64/helm" "${destination}/helm"

"${destination}/kind" version
"${destination}/kubectl" version --client --output=yaml >/dev/null
"${destination}/helm" version --template '{{ .Version }}' >/dev/null
