import NextAuth from 'next-auth'
import Keycloak from 'next-auth/providers/keycloak'

import { authConfig } from './auth.config'

// Keycloak URLs. The browser must use the external URL (localhost:8080) for the
// authorization redirect and for validating the id_token `iss`. Server-side calls
// (token, userinfo, jwks) run inside the container and must use the internal,
// container-reachable URL (civitas-keycloak:8080) when it is set — localhost:8080 is
// unreachable from inside the container. Providing these endpoints explicitly makes
// Auth.js skip OIDC discovery entirely, so it never fetches the unreachable
// localhost:8080/.well-known. Falls back to the external URL when no internal URL is
// set (e.g. running the frontend on the host), which keeps that path working too.
const KC_EXTERNAL = process.env.KEYCLOAK_ISSUER!
const KC_INTERNAL = process.env.KEYCLOAK_INTERNAL_ISSUER ?? KC_EXTERNAL

export const { handlers, auth, signIn, signOut } = NextAuth({
  ...authConfig,
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
      issuer: KC_EXTERNAL,
      authorization: {
        url: `${KC_EXTERNAL}/protocol/openid-connect/auth`,
        params: { scope: 'openid email profile' },
      },
      token: `${KC_INTERNAL}/protocol/openid-connect/token`,
      userinfo: `${KC_INTERNAL}/protocol/openid-connect/userinfo`,
      // Auth.js OIDC endpoint override; the key is a library-defined snake_case field
      // (IssuerMetadata in @auth/core), so it cannot be camelCased.
      // eslint-disable-next-line @typescript-eslint/naming-convention
      jwks_endpoint: `${KC_INTERNAL}/protocol/openid-connect/certs`,
    }),
  ],
})
