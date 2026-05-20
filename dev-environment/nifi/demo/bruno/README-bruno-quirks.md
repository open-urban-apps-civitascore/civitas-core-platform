# Bruno CLI quirks for the NiFi demo

## Always pass `--disable-cookies`

```bash
npx --yes @usebruno/cli@3.3.0 run 01_deploy --env local --insecure --disable-cookies
```

Without `--disable-cookies`, every PUT/POST/DELETE after `01_get_token` returns
HTTP 403 and NiFi logs the caller as anonymous (`-`) instead of `admin`.

### Why

`POST /nifi-api/access/token` responds with both the JWT body **and** a
`Set-Cookie: __Secure-Authorization-Bearer=<JWT>; HttpOnly; Secure; SameSite=Strict`
header. Bruno's CLI maintains a shared cookie jar across requests in a folder
run, so every subsequent request carries this cookie alongside the
`Authorization: Bearer` header.

NiFi treats the bearer cookie as an authentication source separate from the
header. When the cookie is present on a state-changing request (PUT/POST/DELETE)
without a matching CSRF token, NiFi rejects the request as anonymous — even
though a valid `Authorization` header is also present. GETs are unaffected
because NiFi does not enforce CSRF on safe methods, which is why
`02_get_root_pg`, `03_find_demo_pg`, and `04_get_dbcp_service` succeed while
`05`/`06`/`07` fail.

Verified against `nifi-request.log`: with the cookie, PUTs log as `- - [...]`;
with `--disable-cookies`, the same PUTs log as `- admin [...]` and return 200.

## `bru.setVar` vs `bru.setEnvVar` for auth tokens

The collection-level `auth:bearer { token: {{nifiToken}} }` block resolves
variables from the **environment** scope at request-send time, not the
collection-runtime scope set by `bru.setVar`. In `01_get_token.bru` use
`bru.setEnvVar('nifiToken', res.body)`, not `bru.setVar`, or the bearer
header will be empty on all subsequent requests.
