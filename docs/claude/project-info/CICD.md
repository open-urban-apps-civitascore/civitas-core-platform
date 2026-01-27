# CI/CD Pipeline

GitLab CI with separate pipelines for each component.

## Pipeline Stages

`lint` → `test` → `qa` → `build` → `deploy`

## Frontend Pipeline

- **Lint**: ESLint with GitLab code quality reports
- **Test**: Vitest with coverage (Cobertura format)
- **Build**: Next.js production build
- **Deploy**: Docker multi-arch images (amd64/arm64)

## Backend Pipeline

- **Lint**: Spotless check
- **Test**: JUnit + JaCoCo coverage
- **Build**: Maven package
- **Deploy**: Docker multi-arch images

## Config Adapter Pipeline

- **Lint**: Spotless check
- **Test**: JUnit with Testcontainers (requires Docker-in-Docker)
- **Build**: Maven package
- **Deploy**: Docker multi-arch images

## Security

Secret detection, SAST (Advanced), Dependency scanning (runs on all pipelines)

## Pipeline Rules

Jobs only run when relevant files change (path-based filtering in `.gitlab/ci/*.yml`)

## Local Testing

Use `gitlab-ci-local <jobName>` for local pipeline testing.
