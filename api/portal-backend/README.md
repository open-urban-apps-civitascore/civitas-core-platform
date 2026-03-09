# Portal Backend API Collection

OpenAPI spec, interactive docs, and Bruno collections for the Portal Backend service.

## Contents

```
api/portal-backend/
  openapi.yaml        OpenAPI 3.1 spec (source of truth)
  docs.html           Scalar viewer (open in browser)
  bruno-api/          API reference + manual testing (mirrors OpenAPI spec)
  bruno-tests/        Scenario-based test flows
  .redocly.yaml       Linting configuration
  package.json        Redocly CLI dependency
```

## Prerequisites

- [Node.js](https://nodejs.org/) (for Redocly CLI)
- [Bruno](https://www.usebruno.com/) (desktop app or CLI)

## Quick Start

Install Redocly CLI:

```bash
cd api/portal-backend
pnpm install
```

### View API docs

```bash
pnpm api-docs
```

Opens the Scalar API reference at `http://localhost:8095/docs.html`. The page loads the OpenAPI spec via Scalar (CDN).

### Live preview while editing

```bash
pnpm run preview
```

Opens a live-reloading preview at `http://localhost:8080`. Edit `openapi.yaml` in your editor, see changes instantly.

### Lint the spec

```bash
pnpm run lint
```

### Use Bruno collections

1. Open Bruno
2. Open Collection > select `api/portal-backend/bruno-api/` or `api/portal-backend/bruno-tests/`
3. Select the "local" environment
4. Dev credentials are pre-configured — ready to use out of the box

**bruno-api** — one request per endpoint, organized by resource. Use for manual testing during development.

**bruno-tests** — sequential test flows with assertions and response chaining. The `group-management-flow` is an example: creates a group, reads it, updates it, deletes it.

## Editing the OpenAPI spec

Recommended IDE extensions for autocomplete and validation:

- **IntelliJ Ultimate**: Built-in OpenAPI support (no extension needed)
- **VS Code**: [OpenAPI (Swagger) Editor by 42Crunch](https://marketplace.visualstudio.com/items?itemName=42Crunch.vscode-openapi)

Workflow:
1. Edit `openapi.yaml` in your IDE
2. Run `pnpm run preview` for live browser preview (optional)
3. Run `pnpm run lint` before committing
