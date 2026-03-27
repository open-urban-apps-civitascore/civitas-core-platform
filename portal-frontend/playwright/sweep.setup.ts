/**
 * Global sweep that runs once before all E2E tests.
 *
 * Removes stale test data left behind by previous runs that crashed
 * or were interrupted before afterAll cleanup could execute.
 *
 * Identifies e2e resources by naming convention:
 *   - Users: email ends with @e2e.civitas.dev
 *   - Groups/roles: name starts with "e2e-"
 *   - Datasets/datasources/datastructures: name starts with "E2E-"
 */
import { test as setup } from '@playwright/test'

import { TEST_PASSWORD, TEST_USERNAME } from '../playwright.config'
import { ApiClient } from './helpers/api/apiClient'
import { KeycloakClient } from './helpers/api/keycloakClient'

const log = (msg: string) => console.log(`  [sweep] ${msg}`)
const warn = (msg: string) => console.warn(`  [sweep] ⚠ ${msg}`)

/** Fetch a paginated list, returning only items whose name/email matches the e2e pattern. */
const fetchE2eItems = async <T extends { id: string; name?: string }>(
  fetcher: (params: string) => Promise<{ content: T[] }>,
  search: string,
  nameFilter: (item: T) => boolean,
): Promise<T[]> => {
  try {
    const res = await fetcher(`q=${search}&size=200`)
    return res.content.filter(nameFilter)
  } catch {
    return []
  }
}

setup('sweep stale e2e data', async () => {
  setup.setTimeout(60_000)

  let api: ApiClient
  try {
    api = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
  } catch (e) {
    warn(`Could not authenticate — skipping sweep: ${e}`)
    return
  }

  const isE2eName = (name?: string) => !!name && (name.startsWith('e2e-') || name.startsWith('E2E-'))
  const isE2eEmail = (item: { id: string; name?: string; email?: string }) =>
    !!item.email && item.email.endsWith('@e2e.civitas.dev')

  // --- 1. Datasets (before datasources — pipelines reference datasources) ---
  const datasets = await fetchE2eItems(
    p => api.getDatasets(p),
    'E2E-',
    d => isE2eName(d.name),
  )
  for (const d of datasets) {
    try {
      await api.unpublishDataset(d.id).catch(() => {})
      await api.deleteDataset(d.id)
    } catch {
      warn(`dataset ${d.id}`)
    }
  }
  if (datasets.length) log(`Deleted ${datasets.length} datasets`)

  // --- 2. Datasources ---
  const datasources = await fetchE2eItems(
    p => api.getDatasources(p),
    'E2E-',
    d => isE2eName(d.name),
  )
  for (const d of datasources) {
    try {
      await api.unpublishDatasource(d.id).catch(() => {})
      await api.deleteDatasource(d.id)
    } catch {
      warn(`datasource ${d.id}`)
    }
  }
  if (datasources.length) log(`Deleted ${datasources.length} datasources`)

  // --- 3. Datastructures ---
  const datastructures = await fetchE2eItems(
    p => api.getDatastructures(p),
    'E2E-',
    d => isE2eName(d.name),
  )
  for (const d of datastructures) {
    try {
      await api.unpublishDatastructure(d.id).catch(() => {})
      await api.deleteDatastructure(d.id)
    } catch {
      warn(`datastructure ${d.id}`)
    }
  }
  if (datastructures.length) log(`Deleted ${datastructures.length} datastructures`)

  // --- 4. Assignments (must go before groups/roles) ---
  try {
    const assignments = await api.getAssignments('size=500')
    // We can't filter assignments by name — delete all that reference e2e groups
    const e2eGroups = await fetchE2eItems(
      p => api.getGroups(p),
      'e2e-',
      g => isE2eName(g.name),
    )
    const e2eGroupIds = new Set(e2eGroups.map(g => g.id))
    const staleAssignments = assignments.content.filter(a => e2eGroupIds.has(a.groupId))
    for (const a of staleAssignments) {
      try {
        await api.deleteAssignment(a.id)
      } catch {
        warn(`assignment ${a.id}`)
      }
    }
    if (staleAssignments.length) log(`Deleted ${staleAssignments.length} assignments`)
  } catch {
    // Assignments endpoint might not support listing — continue
  }

  // --- 5. Groups ---
  const groups = await fetchE2eItems(
    p => api.getGroups(p),
    'e2e-',
    g => isE2eName(g.name),
  )
  for (const g of groups) {
    try {
      await api.deleteGroup(g.id)
    } catch {
      warn(`group ${g.id}`)
    }
  }
  if (groups.length) log(`Deleted ${groups.length} groups`)

  // --- 6. Roles ---
  const roles = await fetchE2eItems(
    p => api.getRoles(p),
    'e2e-',
    r => isE2eName(r.name),
  )
  for (const r of roles) {
    try {
      await api.deleteRole(r.id)
    } catch {
      warn(`role ${r.id}`)
    }
  }
  if (roles.length) log(`Deleted ${roles.length} roles`)

  // --- 7. Portal users ---
  const users = await fetchE2eItems(
    p => api.getUsers(p) as Promise<{ content: Array<{ id: string; name?: string; email?: string }> }>,
    'e2e.civitas.dev',
    isE2eEmail,
  )
  for (const u of users) {
    try {
      await api.deleteUser(u.id)
    } catch {
      warn(`user ${u.id}`)
    }
  }
  if (users.length) log(`Deleted ${users.length} portal users`)

  // --- 8. Keycloak users ---
  try {
    const kc = await KeycloakClient.create()
    const kcUsers = await kc.searchUsersByEmail('@e2e.civitas.dev')
    const e2eKcUsers = kcUsers.filter(u => u.email?.endsWith('@e2e.civitas.dev'))
    for (const u of e2eKcUsers) {
      try {
        await kc.deleteUser(u.id)
      } catch {
        warn(`keycloak user ${u.id}`)
      }
    }
    if (e2eKcUsers.length) log(`Deleted ${e2eKcUsers.length} Keycloak users`)
  } catch {
    warn('Could not clean Keycloak users')
  }

  log('Sweep complete')
})
