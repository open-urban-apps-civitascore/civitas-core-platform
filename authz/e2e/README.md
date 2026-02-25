# AuthZ E2E Tests

Standalone Playwright tests for CIVITAS authorization integration.

## Prerequisites

1. **Full stack running:**
   ```bash
   # From dev-environment/authz/
   docker compose up -d

   # From portal-frontend/
   pnpm dev
   ```

2. **Test users seeded in Keycloak:**
   ```bash
   cd dev-environment/authz
   ./seed-keycloak-users.sh
   ```

3. **Test users seeded in database:**
   ```bash
   cd dev-environment/authz
   psql -h localhost -U admin -d civitas_authz -f seed-authz-data.sql
   ```

## Running Tests

```bash
cd authz/e2e

# Install dependencies (first time)
npm install

# Run tests
npm test

# Run with UI
npm run test:ui

# Run headed (see browser)
npm run test:headed
```

## Test Users

| Email | Password | Permissions |
|-------|----------|-------------|
| authz.admin@e2e.civitas.dev | authztest123 | All (DataArchitect) |
| authz.reader@e2e.civitas.dev | authztest123 | Read-only (DataConsumer) |
| authz.none@e2e.civitas.dev | authztest123 | None |

## Environment Variables

Override test user credentials:
```bash
E2E_AUTHZ_ADMIN_EMAIL=...
E2E_AUTHZ_ADMIN_PASSWORD=...
E2E_AUTHZ_READER_EMAIL=...
E2E_AUTHZ_READER_PASSWORD=...
E2E_AUTHZ_NOPERMS_EMAIL=...
E2E_AUTHZ_NOPERMS_PASSWORD=...
FRONTEND_URL=http://localhost:3000
```

## Test Scenarios

1. **Happy Path** - Users with correct permissions can access pages
2. **Permission Denied** - Users without permissions get 403
3. **Null Permission** - Any authenticated user can access /users/me
4. **Unauthenticated** - No auth token returns 401
