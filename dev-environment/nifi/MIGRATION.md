# NiFi: Single-User Auth → OIDC — Deployment Migration (#1838)

NiFi no longer uses single-user login. The config-adapter authenticates to NiFi via the OIDC
**client-credentials** grant against Keycloak. This repo ships the dev/CI config; **staging/prod must
be updated by the deployment team** (no Helm/k8s/Terraform lives here).

Three components must line up: **Keycloak → NiFi → config-adapter**.

## 1. Keycloak (in the real realm)

- [ ] Create a confidential client `nifi`: `serviceAccountsEnabled=true`, `standardFlowEnabled=false`,
      `directAccessGrantsEnabled=false`, with a **real** client secret.
- [ ] **Pin the service-account identity.** NiFi maps the bearer token to its **`sub`** claim = the
      Keycloak user id of the service account. Either import the service-account user with a **fixed
      `id`** (as in dev) or read the generated id — you need it in step 2.
- [ ] Keep the client `description` **≤ 255 chars** (Keycloak DB column limit; longer fails the import).

## 2. NiFi server

- [ ] `AUTH=oidc`
- [ ] `NIFI_SECURITY_USER_OIDC_DISCOVERY_URL` → Keycloak realm discovery
- [ ] `NIFI_SECURITY_USER_OIDC_CLIENT_ID=nifi`, `NIFI_SECURITY_USER_OIDC_CLIENT_SECRET=<real secret>`
- [ ] `INITIAL_ADMIN_IDENTITY=<the sub from step 1>`
- [ ] **Provide a real TLS keystore/truststore** (`KEYSTORE_*` / `TRUSTSTORE_*`). Unlike single-user
      mode, OIDC does **not** auto-generate one.

## 3. config-adapter

- [ ] `NIFI_OIDC_TOKEN_URI` → Keycloak realm token endpoint:
      `<keycloak-base>/realms/<realm>/protocol/openid-connect/token`
- [ ] `NIFI_OIDC_CLIENT_ID=nifi`, `NIFI_OIDC_CLIENT_SECRET=<real secret>`
- [ ] Remove `NIFI_USERNAME`, `NIFI_PASSWORD` (no longer read)
- [ ] Prod: `NIFI_TLS_INSECURE=false`

## Must-not-miss

- **One secret, three places:** the client secret must be identical in Keycloak, NiFi
  (`NIFI_SECURITY_USER_OIDC_CLIENT_SECRET`), and config-adapter (`NIFI_OIDC_CLIENT_SECRET`). Rotate in
  all three together.
- **Migrating an existing NiFi with persisted state:** an instance that already has single-user
  `authorizations.xml` / `users.xml` and an old `flow.json.gz` (old sensitive-props key) will **not**
  re-seed the initial admin and may fail to decrypt the old flow → use a **fresh state volume** or
  reset the authorizer state. A brand-new instance is fine.

## Upgrading an existing dev environment

A fresh checkout works out of the box; an **existing** setup needs two one-time steps:

- **Keycloak already has the `civitas-core` realm** → `--import-realm` skips it, so the new `nifi`
  client is never created and token requests fail with `invalid_client`. Simplest fix: recreate the
  Keycloak volume (`docker compose … down -v`) — that re-imports the client with its **pinned**
  service-account id. If you must keep the volume, create the `nifi` client by hand (confidential,
  service accounts on, standard flow off, secret = `NIFI_OIDC_CLIENT_SECRET`), then read the
  resulting service-account user id and set it as NiFi's `INITIAL_ADMIN_IDENTITY` — a hand-created
  client gets a **random** id, not the pinned one, so the constant admin identity no longer matches:
  `kcadm.sh get users -r civitas-core -q username=service-account-nifi --fields id`.
- **Existing `dev-environment/nifi/.env`** has no `NIFI_OIDC_CLIENT_SECRET` → add it (must match the
  realm's `nifi` client secret), otherwise NiFi falls back to the committed dev default.

## Good to know

- **Start order:** the Keycloak realm must be fully imported **before** NiFi starts (NiFi fetches OIDC
  discovery at boot).
- **A 403 on the first deploy is expected:** the config-adapter self-provisions its own root-canvas
  policies on first use (log: `denied 403 → provisioning → Ensured`). Not an error.
- **Security:** the service account is thereby a NiFi full admin (it needs `/policies` to grant itself
  canvas access). Treat the secret accordingly.
- **NiFi UI login:** the single-user login is gone; human UI login via Keycloak is not configured
  (not required). Add a separate authorization-code client if you ever need it.
