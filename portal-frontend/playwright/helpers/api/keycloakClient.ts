/**
 * Keycloak Admin API client for E2E test user lifecycle.
 *
 * Creates and deletes users in Keycloak so they can authenticate.
 * The backend user record is created via the portal API (ApiClient),
 * then linked to Keycloak via the config-adapter event flow.
 *
 * For E2E tests, we create users directly in Keycloak to avoid
 * depending on the async config-adapter event processing.
 */
import { request as pwRequest } from '@playwright/test'

import { getKeycloakAdminToken } from './apiClient'

const KC_URL = process.env.KC_URL ?? 'http://localhost:8080'
const KC_REALM = process.env.KC_REALM ?? 'civitas-core'

export class KeycloakClient {
  private adminToken: string

  constructor(adminToken: string) {
    this.adminToken = adminToken
  }

  static async create(): Promise<KeycloakClient> {
    const token = await getKeycloakAdminToken()
    return new KeycloakClient(token)
  }

  private get baseUrl() {
    return `${KC_URL}/admin/realms/${KC_REALM}`
  }

  /**
   * Create a user in Keycloak with a password. Returns the Keycloak user ID.
   */
  async createUser(opts: {
    email: string
    firstName: string
    lastName: string
    password: string
    enabled?: boolean
  }): Promise<string> {
    const ctx = await pwRequest.newContext()
    try {
      const res = await ctx.post(`${this.baseUrl}/users`, {
        headers: {
          Authorization: `Bearer ${this.adminToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          username: opts.email,
          email: opts.email,
          emailVerified: true,
          enabled: opts.enabled ?? true,
          firstName: opts.firstName,
          lastName: opts.lastName,
          credentials: [
            {
              type: 'password',
              value: opts.password,
              temporary: false,
            },
          ],
        },
      })

      if (res.status() === 409) {
        // User already exists (likely created by config-adapter).
        // Look up the ID, reset the password, mark email verified,
        // and clear required actions so we can log in immediately.
        const kcUserId = await this.getUserIdByEmail(opts.email)
        await this.resetPassword(kcUserId, opts.password)
        await this.enableUser(kcUserId)
        return kcUserId
      }

      if (!res.ok()) {
        const body = await res.text()
        throw new Error(`Keycloak create user failed (${res.status()}): ${body}`)
      }

      // Extract user ID from Location header
      const location = res.headers()['location'] ?? ''
      const kcUserId = location.split('/').pop()
      if (!kcUserId) {
        return this.getUserIdByEmail(opts.email)
      }
      return kcUserId
    } finally {
      await ctx.dispose()
    }
  }

  /**
   * Look up a Keycloak user ID by email.
   */
  async getUserIdByEmail(email: string): Promise<string> {
    const ctx = await pwRequest.newContext()
    try {
      const res = await ctx.get(`${this.baseUrl}/users?email=${encodeURIComponent(email)}&exact=true`, {
        headers: { Authorization: `Bearer ${this.adminToken}` },
      })
      if (!res.ok()) {
        throw new Error(`Keycloak user lookup failed (${res.status()})`)
      }
      const users: Array<{ id: string }> = await res.json()
      if (users.length === 0) {
        throw new Error(`Keycloak user not found: ${email}`)
      }
      return users[0].id
    } finally {
      await ctx.dispose()
    }
  }

  /**
   * Ensure a Keycloak user is enabled, email-verified, and has no required actions.
   */
  async enableUser(kcUserId: string): Promise<void> {
    const ctx = await pwRequest.newContext()
    try {
      const res = await ctx.put(`${this.baseUrl}/users/${kcUserId}`, {
        headers: {
          Authorization: `Bearer ${this.adminToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          emailVerified: true,
          enabled: true,
          requiredActions: [],
        },
      })
      if (!res.ok()) {
        const body = await res.text()
        throw new Error(`Keycloak enable user failed (${res.status()}): ${body}`)
      }
    } finally {
      await ctx.dispose()
    }
  }

  /**
   * Reset a Keycloak user's password (non-temporary).
   */
  async resetPassword(kcUserId: string, password: string): Promise<void> {
    const ctx = await pwRequest.newContext()
    try {
      const res = await ctx.put(`${this.baseUrl}/users/${kcUserId}/reset-password`, {
        headers: {
          Authorization: `Bearer ${this.adminToken}`,
          'Content-Type': 'application/json',
        },
        data: {
          type: 'password',
          value: password,
          temporary: false,
        },
      })
      if (!res.ok()) {
        const body = await res.text()
        throw new Error(`Keycloak reset password failed (${res.status()}): ${body}`)
      }
    } finally {
      await ctx.dispose()
    }
  }

  /**
   * Delete a user from Keycloak by their Keycloak user ID.
   */
  async deleteUser(kcUserId: string): Promise<void> {
    const ctx = await pwRequest.newContext()
    try {
      const res = await ctx.delete(`${this.baseUrl}/users/${kcUserId}`, {
        headers: { Authorization: `Bearer ${this.adminToken}` },
      })
      if (!res.ok() && res.status() !== 404) {
        throw new Error(`Keycloak delete user failed (${res.status()})`)
      }
    } finally {
      await ctx.dispose()
    }
  }

  /**
   * Delete a user by email (convenience method).
   */
  async deleteUserByEmail(email: string): Promise<void> {
    try {
      const kcUserId = await this.getUserIdByEmail(email)
      await this.deleteUser(kcUserId)
    } catch {
      // User doesn't exist — that's fine for cleanup
    }
  }
}
