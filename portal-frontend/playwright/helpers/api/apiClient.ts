/**
 * Backend API client for E2E test setup/teardown.
 *
 * Authenticates via Keycloak OIDC and calls the backend through the APISIX gateway.
 * Used to create/manage test users, groups, roles, and assignments programmatically.
 */
import { request as pwRequest } from '@playwright/test'

// Keycloak config — matches dev-environment defaults
const KC_URL = process.env.KC_URL ?? 'http://localhost:8080'
const KC_REALM = process.env.KC_REALM ?? 'civitas-core'
const KC_CLIENT_ID = process.env.KC_CLIENT_ID ?? 'portal-frontend'
const KC_CLIENT_SECRET = process.env.KC_CLIENT_SECRET ?? 'dev-only-portal-frontend-secret'

// APISIX gateway — same as API_BASE_URL:API_PORT in .env.local
const API_BASE_URL = `${process.env.API_BASE_URL ?? 'http://localhost'}:${process.env.API_PORT ?? '9080'}`

// Keycloak admin credentials (for user creation in Keycloak)
const KC_ADMIN_USER = process.env.KC_ADMIN_USER ?? 'admin'
const KC_ADMIN_PASS = process.env.KC_ADMIN_PASS ?? 'admin'

// eslint-disable-next-line @typescript-eslint/naming-convention -- Keycloak OIDC token response uses snake_case
type TokenResponse = { access_token: string; refresh_token: string; expires_in: number; token_type: string }

/* eslint-disable @typescript-eslint/naming-convention -- OIDC token endpoint requires snake_case params */

/**
 * Get an OIDC access token from Keycloak using resource owner password credentials.
 */
export const getAccessToken = async (username: string, password: string): Promise<string> => {
  const ctx = await pwRequest.newContext()
  try {
    const res = await ctx.post(`${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/token`, {
      form: {
        grant_type: 'password',
        client_id: KC_CLIENT_ID,
        client_secret: KC_CLIENT_SECRET,
        username,
        password,
      },
    })
    if (!res.ok()) {
      const body = await res.text()
      throw new Error(`Keycloak token request failed (${res.status()}): ${body}`)
    }
    const data: TokenResponse = await res.json()
    return data.access_token
  } finally {
    await ctx.dispose()
  }
}

/**
 * Get a Keycloak admin token (master realm) for user management operations.
 */
export const getKeycloakAdminToken = async (): Promise<string> => {
  const ctx = await pwRequest.newContext()
  try {
    const res = await ctx.post(`${KC_URL}/realms/master/protocol/openid-connect/token`, {
      form: {
        grant_type: 'password',
        client_id: 'admin-cli',
        username: KC_ADMIN_USER,
        password: KC_ADMIN_PASS,
      },
    })
    if (!res.ok()) {
      throw new Error(`Keycloak admin token failed (${res.status()})`)
    }
    const data: TokenResponse = await res.json()
    return data.access_token
  } finally {
    await ctx.dispose()
  }
}

/* eslint-enable @typescript-eslint/naming-convention */

/**
 * Authenticated API client that calls the backend through APISIX.
 * Create one per test user/session.
 */
export class ApiClient {
  private token: string

  constructor(token: string) {
    this.token = token
  }

  /**
   * Create an ApiClient authenticated as the given user.
   */
  static async asUser(username: string, password: string): Promise<ApiClient> {
    const token = await getAccessToken(username, password)
    return new ApiClient(token)
  }

  private async request<T>(method: string, path: string, data?: unknown): Promise<T> {
    const ctx = await pwRequest.newContext()
    try {
      const url = `${API_BASE_URL}/v1${path}`
      const options: Parameters<typeof ctx.fetch>[1] = {
        method,
        headers: {
          Authorization: `Bearer ${this.token}`,
          'Content-Type': 'application/json',
        },
      }
      if (data !== undefined) {
        options.data = data
      }
      const res = await ctx.fetch(url, options)
      if (!res.ok()) {
        const body = await res.text()
        throw new Error(`API ${method} ${path} failed (${res.status()}): ${body}`)
      }
      const contentType = res.headers()['content-type'] ?? ''
      if (contentType.includes('application/json')) {
        return (await res.json()) as T
      }
      return undefined as T
    } finally {
      await ctx.dispose()
    }
  }

  async get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path)
  }

  async post<T>(path: string, data: unknown): Promise<T> {
    return this.request<T>('POST', path, data)
  }

  async put<T>(path: string, data: unknown): Promise<T> {
    return this.request<T>('PUT', path, data)
  }

  async patch<T>(path: string, data: unknown): Promise<T> {
    return this.request<T>('PATCH', path, data)
  }

  async delete(path: string): Promise<void> {
    return this.request<void>('DELETE', path)
  }

  // --- Convenience methods ---

  async getUsers(params?: string) {
    return this.get<{ content: unknown[]; totalElements: number }>(`/users${params ? `?${params}` : ''}`)
  }

  async createUser(data: { firstName: string; lastName: string; email: string; title?: string; phone?: string }) {
    return this.post<{ id: string; firstName: string; lastName: string; email: string }>('/users', {
      title: 'OTHER',
      active: true,
      ...data,
    })
  }

  async deleteUser(userId: string) {
    return this.delete(`/users/${userId}`)
  }

  async getRoles(params?: string) {
    return this.get<{ content: Array<{ id: string; name: string; permissions: Array<{ id: string; name: string }> }> }>(
      `/roles${params ? `?${params}` : ''}`,
    )
  }

  async createRole(data: { name: string; description?: string; roleType: string; permissionIds?: string[] }) {
    return this.post<{ id: string; name: string }>('/roles', data)
  }

  async updateRole(
    roleId: string,
    data: { name: string; description?: string; roleType: string; permissionIds?: string[]; readonly?: boolean },
  ) {
    return this.put<{ id: string; name: string }>(`/roles/${roleId}`, data)
  }

  async deleteRole(roleId: string) {
    return this.delete(`/roles/${roleId}`)
  }

  async getPermissions() {
    // Permissions endpoint returns a plain array (not paginated)
    const arr =
      await this.get<Array<{ id: string; name: string; permissionType: string; category: string }>>('/permissions')
    return arr
  }

  async getGroups(params?: string) {
    return this.get<{ content: Array<{ id: string; name: string; members: Array<{ id: string }> | null }> }>(
      `/groups${params ? `?${params}` : ''}`,
    )
  }

  async createGroup(data: { name: string; description?: string; contactUserId?: string; memberIds?: string[] }) {
    return this.post<{ id: string; name: string }>('/groups', { description: '', ...data })
  }

  async updateGroup(
    groupId: string,
    data: { name: string; description?: string; contactUserId?: string; memberIds?: string[] },
  ) {
    return this.put<{ id: string; name: string }>(`/groups/${groupId}`, data)
  }

  async patchGroup(groupId: string, data: Partial<{ name: string; description: string; memberIds: string[] }>) {
    return this.patch<{ id: string; name: string }>(`/groups/${groupId}`, data)
  }

  async deleteGroup(groupId: string) {
    return this.delete(`/groups/${groupId}`)
  }

  async replaceGroupAssignments(
    groupId: string,
    assignments: Array<{ groupId: string; roleId: string; scopeType?: string; scopeId?: string }>,
  ) {
    return this.put<unknown>(`/groups/${groupId}/assignments`, assignments)
  }

  async createAssignment(data: { groupId: string; roleId: string; scopeType?: string; scopeId?: string }) {
    return this.post<{ id: string }>('/assignments', data)
  }

  async deleteAssignment(assignmentId: string) {
    return this.delete(`/assignments/${assignmentId}`)
  }

  // --- Assignments ---

  async getAssignments(params?: string) {
    return this.get<{ content: Array<{ id: string; groupId: string; roleId: string }> }>(
      `/assignments${params ? `?${params}` : ''}`,
    )
  }

  // --- Datasets ---

  async getDatasets(params?: string) {
    return this.get<{ content: Array<{ id: string; name: string }> }>(`/datasets${params ? `?${params}` : ''}`)
  }

  async createDataset(data: { name: string; description?: string }) {
    return this.post<{ id: string; name: string }>('/datasets', data)
  }

  async markReadyDataset(datasetId: string) {
    return this.post<{ id: string; dataSetStatus: string }>(`/datasets/${datasetId}/markReady`, {})
  }

  async markDraftDataset(datasetId: string) {
    return this.post<{ id: string; dataSetStatus: string }>(`/datasets/${datasetId}/markDraft`, {})
  }

  async deleteDataset(datasetId: string) {
    return this.delete(`/datasets/${datasetId}`)
  }

  // --- Pipelines ---

  async createPipeline(datasetId: string, data: { name: string; description?: string; dataSourceIds?: string[] }) {
    return this.post<{ id: string; name: string }>(`/datasets/${datasetId}/pipelines`, {
      styles: {},
      model: {},
      apis: [],
      persistences: [],
      description: '',
      dataSourceIds: [],
      ...data,
    })
  }

  // --- Datasources ---

  async getDatasources(params?: string) {
    return this.get<{ content: Array<{ id: string; name: string }> }>(`/datasources${params ? `?${params}` : ''}`)
  }

  async createDatasource(data: {
    name: string
    description?: string
    connectorType?: string
    configuration?: Record<string, unknown>
    dataStructureVersionId?: string | null
  }) {
    return this.post<{ id: string; name: string; dataSourceStatus: string }>('/datasources', {
      assignments: [],
      ...data,
    })
  }

  async releaseDatasource(datasourceId: string) {
    return this.post<{ id: string; dataSourceStatus: string }>(`/datasources/${datasourceId}/release`, {})
  }

  async unreleaseDatasource(datasourceId: string) {
    return this.post<{ id: string; dataSourceStatus: string }>(`/datasources/${datasourceId}/unrelease`, {})
  }

  async deleteDatasource(datasourceId: string) {
    return this.delete(`/datasources/${datasourceId}`)
  }

  // --- Datastructures ---

  async getDatastructures(params?: string) {
    return this.get<{ content: Array<{ id: string; name: string }> }>(`/datastructures${params ? `?${params}` : ''}`)
  }

  async createDatastructure(data: { name: string; description?: string }) {
    return this.post<{ id: string; name: string }>('/datastructures', {
      description: '',
      createdFromDataSource: false,
      dataStructureVersionIds: [],
      assignments: [],
      ...data,
    })
  }

  async createDatastructureVersion(
    datastructureId: string,
    data: {
      version: string
      description?: string
      modelAtlasUri: string
      modelName: string
      model: string
    },
  ) {
    return this.post<{ id: string; version: string; dataStructureVersionStatus: string }>(
      `/datastructures/${datastructureId}/versions`,
      {
        dataStructureVersionSource: 'OWN',
        description: '',
        styles: {},
        ...data,
      },
    )
  }

  async releaseDatastructureVersion(datastructureId: string, versionId: string) {
    return this.post<{ id: string; dataStructureVersionStatus: string }>(
      `/datastructures/${datastructureId}/versions/${versionId}/release`,
      {},
    )
  }

  async releaseDatastructure(datastructureId: string) {
    return this.post<{ id: string; dataStructureStatus: string }>(`/datastructures/${datastructureId}/release`, {})
  }

  async unreleaseDatastructure(datastructureId: string) {
    return this.post<{ id: string; dataStructureStatus: string }>(`/datastructures/${datastructureId}/unrelease`, {})
  }

  async deleteDatastructure(datastructureId: string) {
    return this.delete(`/datastructures/${datastructureId}`)
  }
}
