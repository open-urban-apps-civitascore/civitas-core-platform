# Portal Backend API Collection

Bruno collections for the Portal Backend service.

```
api/portal-backend/
  openapi.yaml        OpenAPI description, generated — see below, do not edit by hand
  bruno-api/          API reference + manual testing, one request per endpoint
  bruno-tests/        Scenario-based test flows
```

`bruno-api` doubles as the end-to-end acceptance gate: the `api-test-backend` job in
`.gitlab/ci/backend.yml` runs it against a Compose stack. See
[`bruno-api/README.md`](bruno-api/README.md) for the folder layout, environments and
how to run the suite locally.

## Prerequisites

[Bruno](https://www.usebruno.com/) — desktop app, or `npx @usebruno/cli` for the runner.

## Quick start

1. Open Bruno
2. Open Collection → select `api/portal-backend/bruno-api/` or `api/portal-backend/bruno-tests/`
3. Select the `local-direct` environment
4. Dev credentials are pre-configured — ready to use out of the box

## API documentation

The backend serves its own OpenAPI description from springdoc. With the backend running:

| | |
|---|---|
| Swagger UI | http://localhost:8089/v1/swagger-ui.html |
| OpenAPI YAML | http://localhost:8089/v1/api-docs.yaml |

The document is assembled in `portal-backend/.../configuration/OpenApiConfig.java` and the
springdoc annotations on the controllers.

`openapi.yaml` in this directory is a committed copy of that description, kept in sync by CI:
`export-openapi-spec` exports it from the backend built in the pipeline (job artifact
`openapi.generated.yaml`), and on `develop` the `update-openapi-spec` job opens a merge request
from the branch `ci/openapi-spec-update` whenever the export differs from the committed file
(`.gitlab/ci/openapi.yml`). To change the spec, change the backend — the next `develop`
pipeline refreshes the merge request.
