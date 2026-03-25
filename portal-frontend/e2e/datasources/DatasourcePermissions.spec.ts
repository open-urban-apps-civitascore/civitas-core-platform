/**
 * E2E tests for datasource permission gating.
 * Tests table view (create button, delete menu) and detail view (edit button)
 * with both TENANT-scoped and entity-scoped permissions.
 */
import { registerEntityPermissionTests } from '../data-entities/dataEntityPermissionTests'

registerEntityPermissionTests({
  name: 'Datasource',
  path: 'datasources',
  tableTestId: 'datasourcesTable',
  createButtonTestId: 'addDatasourceButton',
  permissionPrefix: 'DATASOURCE',
  scopeType: 'DATASOURCE',
  prefix: 'src',
  resourceKey: 'datasourceIds',
  createEntity: (api, name) => api.createDatasource({ name }),
})
