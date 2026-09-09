# Portal Backend API Collection

Bruno collections for the Portal Backend service.

```
api/portal-backend/
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

The backend serves its own OpenAPI description from springdoc; there is no committed
spec file. With the backend running:

| | |
|---|---|
| Swagger UI | http://localhost:8089/v1/swagger-ui.html |
| OpenAPI YAML | http://localhost:8089/v1/api-docs.yaml |

The document is assembled in `portal-backend/.../configuration/OpenApiConfig.java` and the
springdoc annotations on the controllers.
