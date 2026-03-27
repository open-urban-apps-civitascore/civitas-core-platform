/**
 * Parameterized E2E tests for data entity permission gating.
 *
 * Generates test.describe blocks for list view (create button, delete menu)
 * and detail view (edit button) — both TENANT-scoped and entity-scoped.
 */
import { expect, test } from '@playwright/test'

import { TEST_PASSWORD, TEST_USERNAME } from '../../playwright.config'
import {
  ApiClient,
  cleanupTestResources,
  createTestUserWithPermissions,
  emptyResources,
  type TestResources,
  type TestUserProfile,
  uid,
} from '../../playwright/helpers/api'
import { withTestUser } from '../utils/withTestUser'

export type EntityConfig = {
  /** Display name for test titles (e.g. "Datastructure") */
  name: string
  /** URL path segment (e.g. "datastructures") */
  path: string
  /** data-testid of the table element */
  tableTestId: string
  /** data-testid of the create button */
  createButtonTestId: string
  /** Permission prefix (e.g. "DATASTRUCTURE") */
  permissionPrefix: string
  /** Scope type for entity-scoped assignments (e.g. "DATASTRUCTURE") */
  scopeType: string
  /** Short prefix for emails/lastnames to avoid collisions (e.g. "ds") */
  prefix: string
  /** Resource key in TestResources for cleanup (e.g. "datastructureIds") */
  resourceKey: keyof TestResources
  /** Function to create an entity via API, returns { id, name } */
  createEntity: (api: ApiClient, name: string) => Promise<{ id: string; name: string }>
}

export const registerEntityPermissionTests = (config: EntityConfig) => {
  const {
    name,
    path,
    tableTestId,
    createButtonTestId,
    permissionPrefix,
    scopeType,
    prefix,
    resourceKey,
    createEntity,
  } = config

  const CREATE = `${permissionPrefix}_CREATE`
  const READ = `${permissionPrefix}_READ`
  const DELETE = `${permissionPrefix}_DELETE`
  const UPDATE = `${permissionPrefix}_UPDATE`

  // --- List: TENANT scope ---

  test.describe(`${name}s List — Permission Gating (TENANT scope)`, () => {
    test.setTimeout(90_000)

    let adminApi: ApiClient
    let resources: TestResources
    let tenantAdmin: TestUserProfile
    let readOnly: TestUserProfile

    test.beforeAll(async () => {
      adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
      resources = emptyResources()

      tenantAdmin = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}Admin${uid()}`,
          email: `e2e-${prefix}-admin-${uid()}@e2e.civitas.dev`,
          permissions: [CREATE, READ, DELETE],
          roleType: 'DATA',
          scopeType: 'TENANT',
        },
        resources,
      )

      readOnly = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}RO${uid()}`,
          email: `e2e-${prefix}-ro-${uid()}@e2e.civitas.dev`,
          permissions: [READ],
          roleType: 'DATA',
          scopeType: 'TENANT',
        },
        resources,
      )
    })

    test.afterAll(async () => {
      await cleanupTestResources(adminApi, resources)
    })

    test(`Create button visible with ${CREATE} (TENANT)`, async ({ browser }) => {
      await withTestUser(browser, tenantAdmin, async page => {
        await page.goto(`/${path}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId(tableTestId)).toBeVisible({ timeout: 20_000 })
        await expect(page.getByTestId(createButtonTestId)).toBeVisible()
      })
    })

    test(`Create button hidden without ${CREATE} (TENANT)`, async ({ browser }) => {
      await withTestUser(browser, readOnly, async page => {
        await page.goto(`/${path}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId(tableTestId)).toBeVisible({ timeout: 20_000 })
        await expect(page.getByTestId(createButtonTestId)).not.toBeVisible()
      })
    })

    test(`Delete menu visible with ${DELETE} (TENANT)`, async ({ browser }) => {
      const entity = await createEntity(adminApi, `E2E-${prefix}-del-${uid()}`)
      resources[resourceKey].push(entity.id)

      await withTestUser(browser, tenantAdmin, async page => {
        await page.goto(`/${path}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId(tableTestId)).toBeVisible({ timeout: 20_000 })

        const row = page.getByRole('row').filter({ hasText: entity.name })
        await expect(row).toBeVisible({ timeout: 10_000 })
        await expect(row.getByRole('button').last()).toBeVisible()
      })
    })

    test(`Delete menu hidden without ${DELETE} (TENANT)`, async ({ browser }) => {
      const entity = await createEntity(adminApi, `E2E-${prefix}-nodel-${uid()}`)
      resources[resourceKey].push(entity.id)

      await withTestUser(browser, readOnly, async page => {
        await page.goto(`/${path}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId(tableTestId)).toBeVisible({ timeout: 20_000 })

        const row = page.getByRole('row').filter({ hasText: entity.name })
        await expect(row).toBeVisible({ timeout: 10_000 })
        await expect(row.getByRole('button', { name: 'Open menu' })).not.toBeVisible()
      })
    })
  })

  // --- List: entity scope ---

  test.describe(`${name}s List — Permission Gating (entity scope)`, () => {
    test.setTimeout(90_000)

    let adminApi: ApiClient
    let resources: TestResources
    let entity: { id: string; name: string }
    let scopedUser: TestUserProfile
    let unscopedUser: TestUserProfile

    test.beforeAll(async () => {
      adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
      resources = emptyResources()

      entity = await createEntity(adminApi, `E2E-${prefix}-scoped-${uid()}`)
      resources[resourceKey].push(entity.id)

      scopedUser = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}Scoped${uid()}`,
          email: `e2e-${prefix}-scoped-${uid()}@e2e.civitas.dev`,
          permissions: [READ, DELETE],
          scopeType,
          scopeId: entity.id,
          roleType: 'DATA',
        },
        resources,
      )

      unscopedUser = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}Unscoped${uid()}`,
          email: `e2e-${prefix}-unscoped-${uid()}@e2e.civitas.dev`,
          permissions: [READ],
          scopeType,
          scopeId: entity.id,
          roleType: 'DATA',
        },
        resources,
      )
    })

    test.afterAll(async () => {
      await cleanupTestResources(adminApi, resources)
    })

    test(`Delete menu visible with entity-scoped ${DELETE}`, async ({ browser }) => {
      await withTestUser(browser, scopedUser, async page => {
        await page.goto(`/${path}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId(tableTestId)).toBeVisible({ timeout: 20_000 })

        const row = page.getByRole('row').filter({ hasText: entity.name })
        await expect(row).toBeVisible({ timeout: 10_000 })
        await expect(row.getByRole('button').last()).toBeVisible()
      })
    })

    test(`Delete menu hidden without entity-scoped ${DELETE}`, async ({ browser }) => {
      await withTestUser(browser, unscopedUser, async page => {
        await page.goto(`/${path}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId(tableTestId)).toBeVisible({ timeout: 20_000 })

        const row = page.getByRole('row').filter({ hasText: entity.name })
        await expect(row).toBeVisible({ timeout: 10_000 })
        await expect(row.getByRole('button', { name: 'Open menu' })).not.toBeVisible()
      })
    })
  })

  // --- Detail: TENANT scope ---

  test.describe(`${name} Detail — Permission Gating (TENANT scope)`, () => {
    test.setTimeout(90_000)

    let adminApi: ApiClient
    let resources: TestResources
    let entity: { id: string; name: string }
    let userWithUpdate: TestUserProfile
    let readOnly: TestUserProfile

    test.beforeAll(async () => {
      adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
      resources = emptyResources()

      entity = await createEntity(adminApi, `E2E-${prefix}-detail-${uid()}`)
      resources[resourceKey].push(entity.id)

      userWithUpdate = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}Upd${uid()}`,
          email: `e2e-${prefix}-upd-${uid()}@e2e.civitas.dev`,
          permissions: [READ, UPDATE],
          roleType: 'DATA',
          scopeType: 'TENANT',
        },
        resources,
      )

      readOnly = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}DetailRO${uid()}`,
          email: `e2e-${prefix}-detailro-${uid()}@e2e.civitas.dev`,
          permissions: [READ],
          roleType: 'DATA',
          scopeType: 'TENANT',
        },
        resources,
      )
    })

    test.afterAll(async () => {
      await cleanupTestResources(adminApi, resources)
    })

    test(`Edit button visible with ${UPDATE} (TENANT)`, async ({ browser }) => {
      await withTestUser(browser, userWithUpdate, async page => {
        await page.goto(`/${path}/${entity.id}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId('pageHeader').first()).toBeVisible({ timeout: 20_000 })
        await expect(page.getByTestId('editButton')).toBeVisible()
      })
    })

    test(`Edit button hidden without ${UPDATE} (TENANT)`, async ({ browser }) => {
      await withTestUser(browser, readOnly, async page => {
        await page.goto(`/${path}/${entity.id}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId('pageHeader').first()).toBeVisible({ timeout: 20_000 })
        await expect(page.getByTestId('editButton')).not.toBeVisible()
      })
    })
  })

  // --- Detail: entity scope ---

  test.describe(`${name} Detail — Permission Gating (entity scope)`, () => {
    test.setTimeout(90_000)

    let adminApi: ApiClient
    let resources: TestResources
    let entity: { id: string; name: string }
    let scopedUser: TestUserProfile
    let readOnly: TestUserProfile

    test.beforeAll(async () => {
      adminApi = await ApiClient.asUser(TEST_USERNAME, TEST_PASSWORD)
      resources = emptyResources()

      entity = await createEntity(adminApi, `E2E-${prefix}-detail-scoped-${uid()}`)
      resources[resourceKey].push(entity.id)

      scopedUser = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}ScopedUpd${uid()}`,
          email: `e2e-${prefix}-scoped-upd-${uid()}@e2e.civitas.dev`,
          permissions: [READ, UPDATE],
          scopeType,
          scopeId: entity.id,
          roleType: 'DATA',
        },
        resources,
      )

      readOnly = await createTestUserWithPermissions(
        adminApi,
        {
          firstName: 'E2E',
          lastName: `${prefix}ScopedRO${uid()}`,
          email: `e2e-${prefix}-scoped-ro-${uid()}@e2e.civitas.dev`,
          permissions: [READ],
          scopeType,
          scopeId: entity.id,
          roleType: 'DATA',
        },
        resources,
      )
    })

    test.afterAll(async () => {
      await cleanupTestResources(adminApi, resources)
    })

    test(`Edit button visible with entity-scoped ${UPDATE}`, async ({ browser }) => {
      await withTestUser(browser, scopedUser, async page => {
        await page.goto(`/${path}/${entity.id}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId('pageHeader').first()).toBeVisible({ timeout: 20_000 })
        await expect(page.getByTestId('editButton')).toBeVisible()
      })
    })

    test(`Edit button hidden without entity-scoped ${UPDATE}`, async ({ browser }) => {
      await withTestUser(browser, readOnly, async page => {
        await page.goto(`/${path}/${entity.id}`)
        await page.waitForLoadState('domcontentloaded')
        await expect(page.getByTestId('pageHeader').first()).toBeVisible({ timeout: 20_000 })
        await expect(page.getByTestId('editButton')).not.toBeVisible()
      })
    })
  })
}
