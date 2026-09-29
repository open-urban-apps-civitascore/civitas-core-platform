<!-- VENDORED from agent-context@37494c39 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

# Deployment Requirements

## Naming Conventions

Files and directories are **kebab-case**, 3–50 characters. No uppercase, no
underscores, no camelCase or PascalCase, no unicode. No empty files, no
duplicates.

Exceptions, because an upstream convention wins: Helm's own files
(`Chart.yaml`, `NOTES.txt`, `_helpers.tpl`, `.helmignore`), `*.yaml.gotmpl`,
`tests/system/` (Java and Robot Framework name their own way), community
standard files (`CONTRIBUTION_GUIDE.md`, `SECURITY.md`, …) and
`.gitlab/merge_request_templates/`.

Enforced by a pre-commit hook against `.ci/configs/.naming-convention.yaml` —
check with `pre-commit run --all-files`.

## Version Management

- **All chart versions pinned with hashes** (no `latest`)
- **All image tags pinned with hashes** (no `latest`)

## High Availability

For production environments:

- **Replicas**: Use HorizontalPodAutoscaler (HPA) for dynamic scaling, or set minimum 2 replicas for stateless applications
- **Rolling updates** (where accessible): Configure `maxUnavailable: 0`, `maxSurge: 25%`
- **PodDisruptionBudget** (where accessible): Ensure at least 1 pod available during updates

## Resource Management

Set appropriate limits for production and development environments.

- **Set CPU & memory requests** (for scheduling)
- **Set CPU & memory limits** (prevent resource exhaustion)

## Security

- **Containers run as non-root** with `readOnlyRootFilesystem`
- **Secrets**: No plaintext secrets, use secure secret management
- **RBAC** (if applicable): ServiceAccount with least privilege where required
- **NetworkPolicy**: Default-deny with minimal allowed traffic

## Observability & Reliability

- **Health probes** (where accessible): Configure startup, readiness, and liveness probes for long-running applications (exclude jobs and init containers)
- **Graceful shutdown** (where accessible): Set `terminationGracePeriodSeconds` + `preStop` hook
- **Standard labels**: For custom Helm charts, implement [Kubernetes recommended labels](https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/)

## Networking

- **Named ports** in Services (where accessible)
- **In-Cluster Encryption** TLS is handled in-cluster by linkerd, no need to TLS secure ports individually
