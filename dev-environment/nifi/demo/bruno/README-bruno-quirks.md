# Bruno CLI quirks for the NiFi demo

> **Authentication:** NiFi is secured with OpenID Connect. The `get_token`
> requests obtain an access token from Keycloak via the client-credentials grant
> (the `nifi` service account) and stash `access_token` in `nifiToken`; NiFi
> validates it against Keycloak. There is no NiFi single-user `/access/token`
> endpoint anymore.

## Cookie/CSRF gotcha (handled automatically — incl. multipart upload)

Historically `POST /nifi-api/access/token` set a
`Set-Cookie: __Secure-Authorization-Bearer=<JWT>; HttpOnly; Secure; SameSite=Strict`
header alongside the JWT body. Under OIDC the demo no longer calls that endpoint,
so the cookie is not set — but the collection-level pre-request `clear()` (below)
is kept as a harmless safeguard. Bruno's CLI otherwise maintains a shared cookie
jar across requests in a folder run.

NiFi treats the bearer cookie as an authentication source separate from the
header. When the cookie is present on a state-changing request (PUT/POST/DELETE)
without a matching CSRF token, NiFi rejects the request as anonymous — even
though a valid `Authorization` header is also present. GETs are unaffected
because NiFi does not enforce CSRF on safe methods.

The collection-level `script:pre-request` in `collection.bru` calls
`bru.cookies.jar().clear()` before every request, which neutralises this. No
CLI flag needed; works in both Bruno CLI and Bruno Desktop. Verified against
`nifi-request.log`: PUTs log as `- admin [...]` with 200 instead of `- - [...]`
with 403.

(Equivalent CLI-only fix exists as `--disable-cookies` if you ever strip the
pre-request script.)

The collection's multipart upload (`01_deploy/03_upload_snapshot.bru`) hits the
same root cause as PUTs — earlier debugging traced it incorrectly to a separate
"Bruno strips Authorization on multipart-form" bug; in reality there is only
one bug (cookie + CSRF), and the pre-request `clear()` handles it for both
multipart POSTs and JSON PUTs equally.

## `bru.setVar` vs `bru.setEnvVar` for auth tokens

The collection-level `auth:bearer { token: {{nifiToken}} }` block resolves
variables from the **environment** scope at request-send time, not the
collection-runtime scope set by `bru.setVar`. In `01_get_token.bru` use
`bru.setEnvVar('nifiToken', res.body)`, not `bru.setVar`, or the bearer
header will be empty on all subsequent requests.

## Run folders, not single requests

Each folder (`01_deploy`, `02_verify`, `03_cleanup`) starts with its own
token-fetch + PG-lookup prelude that refreshes the runtime env vars
(`nifiToken`, `rootPgId`, `demoPgId`, `dbcpServiceId`, `dbcpRevision`). If you
trigger a single request from inside Bruno Desktop without running the
prelude, you'll hit stale env vars from the previous session — symptoms range
from `"Signed JWT rejected"` (stale token) to `404 Unable to locate controller
service with id 'abc...'` (stale `dbcpServiceId` pointing at a CS that was
deleted with a previous PG).

Always run the folder, not the individual `.bru` file. In Bruno Desktop:
right-click the folder → Run. In CLI: `bru run 01_deploy --env local --insecure`.

## Recovering from a stuck state

If a deploy was interrupted, or `01_deploy` ran twice without cleanup in between,
you can end up with:

- **Duplicate PGs** with the same name (`civitas-mqtt-postgis-demo`) competing
  for the same MQTT topic — data may or may not reach PostGIS depending on which
  PG "wins" the subscription.
- **Queue not empty** errors on PG delete (`HTTP 409 "Queue not empty for ..."`),
  because NiFi refuses to delete a PG that still has queued FlowFiles.
- **Controller-services still ENABLING/DISABLING** when delete fires — `409`
  again, because the PG state isn't yet stable.

The Bruno `03_cleanup` folder only handles the happy path (one PG, empty
queues, fast disable). For the messy case, run the shell helper:

```bash
cd dev-environment/nifi/demo
./scripts/cleanup.sh
```

It loops over **all** matching PGs, drops FlowFiles from every connection,
polls until controller services reach `DISABLED`, then deletes. Idempotent —
re-running on an already-clean root prints `"nothing to clean up"` and exits 0.

## Stale-token symptom: "Signed JWT rejected"

If a request fails with

```
Unauthorized error="invalid_token",
error_description="Signed JWT rejected: ... no matching key(s) found"
```

the `nifiToken` cached in Bruno's runtime env has expired, or Keycloak was
restarted with a fresh signing keypair, so NiFi can no longer validate it
against the realm's JWKS. Re-run `01_get_token.bru` (or the `00_get_token.bru`
in whichever folder you're driving) to mint a fresh token from Keycloak. With
`bru run 01_deploy/02_verify/03_cleanup` this happens automatically; only
single-file runs need a manual token refresh first.
