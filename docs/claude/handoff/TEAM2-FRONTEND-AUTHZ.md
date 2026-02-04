# Team 2 Handoff: Frontend AuthZ Integration

## Overview

This document describes the **minimal changes** made to the portal-frontend to enable authorization via APISIX gateway. The goal is to route API requests through APISIX so OPA can enforce permissions.

## Architecture

```
Browser → Next.js BFF → APISIX (9080) → Portal Backend (8089)
                ↓
         OPA validates permissions
```

The BFF (Backend-for-Frontend) pattern routes requests based on the `x-api-request` header:
- With header: Route through APISIX for authorization
- Without header: Direct to backend (legacy, should be phased out)

## Changes Made

### 1. API Request Header (`x-api-request: 'true'`)

**Purpose:** Signals to the BFF route handler that this request should go through APISIX for authorization.

**Files modified:**

| File | Change |
|------|--------|
| `src/app/services/api/request/apiRequest.ts` | Added `'x-api-request': 'true'` header for client-side requests |
| `src/app/services/api/users/serverRequests.ts` | Added header to `getUsers()`, `getUser()` |
| `src/app/services/api/datasets/serverRequests.ts` | Added header to `getDatasets()` |
| `src/app/services/api/dataspaces/serverRequests.ts` | Added header to `getDataspaces()` |
| `src/app/services/api/groups/serverRequests.ts` | Added header to `getGroups()` |
| `src/app/services/api/datasources/serverRequests.ts` | Added header to `getDatasources()`, `getDatasource()` |

**Example change:**
```typescript
// Before
headers: { ...(await getServerRequestHeaders()) },

// After
headers: { ...(await getServerRequestHeaders()), 'x-api-request': 'true' },
```

### 2. BFF Route Handler

**File:** `src/app/api/[...path]/route.ts`

The route handler checks for `x-api-request` header and routes accordingly:

```typescript
const isApiRequest = request.headers.get('x-api-request') === 'true'
const backendUrl = isApiRequest
  ? process.env.APISIX_URL    // http://localhost:9080
  : process.env.BACKEND_URL   // http://localhost:8089
```

**Environment variables required:**
```bash
APISIX_URL=http://localhost:9080
BACKEND_URL=http://localhost:8089
```

### 3. E2E Tests (Separate)

AuthZ E2E tests are in a **separate standalone project** to avoid polluting the main frontend tests:

**Location:** `authz/e2e/`

```bash
cd authz/e2e
npm install
npm test
```

See `authz/e2e/README.md` for full documentation.

## What's NOT AuthZ-Related

The branch contains many changes from merging `develop` that are **unrelated** to AuthZ:

- `json-server/db.json` - Mock data schema updates (creator→contact, etc.)
- `playwright.config.ts` - JSON_SERVER env vars, retry settings, trace/screenshot settings
- Component changes (datasources, UML modeler, groups, roles, etc.)
- UI/styling changes
- Type definition updates

These changes happened to be on the branch but are not part of the AuthZ feature.

## Migration Guide

### For new API endpoints

When adding new API endpoints, include the `x-api-request` header:

```typescript
// In serverRequests.ts (SSR)
export const getNewResource = async () =>
  apiRequest<NewResource[]>({
    endpoint: '/new-resource',
    method: 'GET',
    headers: { ...(await getServerRequestHeaders()), 'x-api-request': 'true' },
    errorMessage: 'Error fetching new resource.',
  })
```

Client-side requests via `apiRequest.ts` automatically include the header.

### For testing

Run AuthZ-specific tests:
```bash
pnpm test:e2e --project=authzIntegration
pnpm test:e2e --project=noAuth
```

## Verification

1. **Check APISIX routing:**
   ```bash
   # Should route through APISIX (check OPA logs for decision)
   curl -H "Authorization: Bearer $TOKEN" http://localhost:3000/api/users
   ```

2. **Check authorization enforcement:**
   - User with READ_USER permission: Can view /users
   - User without permission: Gets 403

## Dependencies

- APISIX running on port 9080 with OPA plugin configured
- OPA running on port 8181 with civitas policies loaded
- AuthZ Repository running on port 8091
- Keycloak for JWT validation

## Questions?

Contact the AuthZ team or see:
- [TEAM3-AUTHZ-DEPLOYMENT.md](./TEAM3-AUTHZ-DEPLOYMENT.md) - Deployment guide
- [TEAM1-APISIX-CONFIG-ADAPTER.md](./TEAM1-APISIX-CONFIG-ADAPTER.md) - APISIX configuration
