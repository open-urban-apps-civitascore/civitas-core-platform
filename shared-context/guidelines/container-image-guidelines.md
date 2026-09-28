<!-- VENDORED from agent-context@4c3d61f9 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@e2f8a6fc · version 2.0-rc2 · Development/Code_Style_Guides/Container_Image_Guidelines.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/Development/Code_Style_Guides/Container_Image_Guidelines -->

---
sidebar_position: 5
---

# Secure Container Image Guidelines

These guidelines describe how to build and maintain the container images that host CIVITAS/CORE backend services. They complement the backend architecture guide and broader security practices for deployment.

## 1. Scope and Goals

* Provide a **repeatable recipe** for building backend images.
* Reduce the **attack surface** of every service container.
* Ensure images can pass the **GitLab container scanning** pipeline.
* Keep runtime behavior aligned with the operating assumptions of Kubernetes and the CIVITAS/CORE deployment inventory.

## 2. Base Image Strategy

1. **Use verified base images** (e.g., `registry.access.redhat.com/ubi-minimal`, `eclipse-temurin`, or distro LTS images) that receive timely patches.
2. **Pin exact digests/tags** (`FROM eclipse-temurin:21.0.5_11-jre@sha256:…`) so future rebuilds are reproducible and diffable.
3. Prefer **distroless/minimal** bases for final stages; keep glibc/alpine variants aligned with upstream support matrices.
4. Track base image updates and proactively raise upgrade issues when GitLab scanning reports critical CVEs in the base layer.

## 3. Multi-Stage Build Pattern

1. **Builder stage**
   * Install compilers, package managers, and other heavy tooling.
   * Run unit/integration tests where feasible before producing artifacts.
2. **Runtime stage**
   * Copy only the compiled artifacts plus minimal runtime dependencies (JRE, configuration templates, entrypoint scripts).
   * Remove package managers (`apt`, `apk`, `yum`) and temp directories before finalizing the layer.
3. Enable Docker BuildKit to leverage layer caching and `--mount=type=cache` for dependency downloads without bloating the final image.

## 4. Dependency and Artifact Hygiene

* Keep **dependency manifests** (Maven/Gradle) inside the repo and avoid downloading unsigned binaries at build time.
* **Vendor third-party binaries** in a known location and verify checksums before copying them into the image.
* Clean `~/.m2`, `node_modules`, build caches, and test reports between stages so they do not leak into the runtime layer.

## 5. Runtime User and File System

1. Create an explicit, non-root user in the runtime stage and switch via `USER app`.
2. Declare writable paths (`/var/lib/app`, `/tmp`) and ensure ownership/permissions are set during build time.
3. Keep the root filesystem **read-only** at runtime where possible; write mutable data to Kubernetes volumes or emptyDir mounts.
4. Never rely on shell access as root; all operational scripts should run under the dedicated user.

## 6. Configuration and Secrets

* **Do not bake secrets** (tokens, passwords, TLS material) into the image. Use environment variables, Kubernetes secrets, or Vault integrations at runtime.
* Template configuration files to read from environment variables instead of hard-coded values.
* Validate that default configuration disables debug endpoints and uses production-safe settings (e.g., debug mode disabled, verbose error messages disabled, development features disabled).
* Expose only the required ports and describe them in the Helm/inventory values to avoid accidental service exposure.

## 7. Hardening Steps

1. Install only required OS packages; remove package repositories after installation to prevent downgrade attacks.
2. Disable or remove interpreters (bash, curl, package managers) from the final image unless absolutely necessary for runtime.
3. Include health/ready endpoints and run self-checks at container start to fail fast when configuration is invalid.
4. Ensure logs are emitted to `stdout/stderr` with structured logging so clusters can aggregate them centrally.

## 8. Review Checklist

Before opening a merge request for container changes, confirm the following:

- [ ] `Dockerfile` uses a pinned, supported base image and follows the multi-stage pattern.
- [ ] Runtime user is non-root and only necessary files are copied.
- [ ] No secrets, sample credentials, or developer SSH keys remain in any layer.
- [ ] Image has been smoke-tested locally with representative configuration.
- [ ] Documentation/inventory updates accompany any change to ports, volumes, or environment variables.

Following this checklist keeps backend container images compliant with CIVITAS/CORE security expectations and reduces surprises during deployment rollouts.
