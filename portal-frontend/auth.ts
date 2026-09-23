import NextAuth from 'next-auth'
import Keycloak from 'next-auth/providers/keycloak'

import { authConfig } from './auth.config'

// The browser needs the external URL for the redirect and the id_token `iss`; server-side
// calls need the container-reachable one, where it is set.
const KC_EXTERNAL = process.env.KEYCLOAK_ISSUER!
const KC_INTERNAL = process.env.KEYCLOAK_INTERNAL_ISSUER

// Pinning these endpoints skips OIDC discovery, leaving Auth.js to expect the RS256 the
// local realm signs with; without them discovery supplies the realm's own algorithms.
const internalEndpoints = KC_INTERNAL
  ? {
      token: `${KC_INTERNAL}/protocol/openid-connect/token`,
      userinfo: `${KC_INTERNAL}/protocol/openid-connect/userinfo`,
      // Library-defined snake_case field (IssuerMetadata), so it cannot be camelCased.
      // eslint-disable-next-line @typescript-eslint/naming-convention
      jwks_endpoint: `${KC_INTERNAL}/protocol/openid-connect/certs`,
    }
  : {}

export const keycloakProvider = Keycloak({
  clientId: process.env.KEYCLOAK_CLIENT_ID!,
  clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
  issuer: KC_EXTERNAL,
  authorization: {
    url: `${KC_EXTERNAL}/protocol/openid-connect/auth`,
    params: { scope: 'openid email profile' },
  },
  ...internalEndpoints,
})

export const { handlers, auth, signIn, signOut } = NextAuth({
  ...authConfig,
  providers: [keycloakProvider],
})
