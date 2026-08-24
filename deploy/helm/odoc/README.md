# Optional Kubernetes deployment

Docker Compose is the normal way to run Odoc. Use this chart only when you already want to run the same small API/frontend pair on Kubernetes.

The chart does **not** install PostgreSQL or object storage. Point it at services you already operate. Start with the values in `values.yaml`; use `examples/production-values.example.yaml` as a secret-free outline for an external PostgreSQL database, S3-compatible media store, and required server-managed encryption key.

## Try it safely with kind

`kind` runs a disposable Kubernetes cluster inside Docker. `kubectl` talks to that cluster, and Helm installs the chart into it.

Install the three tools without sudo (or use your package manager):

```bash
mkdir -p "$HOME/.local/bin"
./odoc/deploy/helm/odoc/scripts/install-kind-tooling.sh "$HOME/.local/bin"
export PATH="$HOME/.local/bin:$PATH"
```

From the parent directory containing both repositories, run:

```bash
./odoc/deploy/helm/odoc/scripts/kind-smoke.sh
```

The smoke script builds local images, creates a `kind` cluster called `odoc-kind-smoke`, installs a disposable PostgreSQL instance plus Odoc, checks the web app and API, then uninstalls the release and deletes the cluster even if a check fails. It does not configure media storage; that is optional for this Kubernetes smoke.

## Install on an existing cluster

Create a database secret (key names match the defaults):

```bash
kubectl create namespace odoc
kubectl -n odoc create secret generic odoc-database \
  --from-literal=username=odoc --from-literal=password='replace-me'
kubectl -n odoc create secret generic odoc-encryption \
  --from-literal=wrapping-key-base64='replace-with-a-base64-encoded-32-byte-key'
helm upgrade --install odoc ./odoc/deploy/helm/odoc -n odoc \
  --set api.image.repository=your-registry/odoc-api \
  --set api.image.tag=your-tag \
  --set frontend.image.repository=your-registry/odoc-frontend \
  --set frontend.image.tag=your-tag
```

Expose the frontend with your ingress controller or a port-forward:

```bash
kubectl -n odoc port-forward service/odoc-frontend 8081:80
```

For media, set `api.objectStorage.enabled=true`, the endpoint/bucket fields, and a Secret whose keys match `accessKeyKey` and `secretKeyKey`. The encryption Secret is required and must contain a base64-encoded 32-byte wrapping key. These are server-managed settings, not end-to-end encryption.

## Remove it

```bash
helm uninstall odoc -n odoc
kubectl delete namespace odoc

# Only for the disposable kind tutorial:
kind delete cluster --name odoc-kind-smoke
```
