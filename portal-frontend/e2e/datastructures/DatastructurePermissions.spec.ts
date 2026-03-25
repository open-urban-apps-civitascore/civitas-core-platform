/**
 * E2E tests for datastructure permission gating.
 * Tests table view (create button, delete menu) and detail view (edit button)
 * with both TENANT-scoped and entity-scoped permissions.
 */
import { registerEntityPermissionTests } from '../data-entities/dataEntityPermissionTests'

registerEntityPermissionTests({
  name: 'Datastructure',
  path: 'datastructures',
  tableTestId: 'DatastructuresTable',
  createButtonTestId: 'addDatastructureButton',
  permissionPrefix: 'DATASTRUCTURE',
  scopeType: 'DATASTRUCTURE',
  prefix: 'ds',
  resourceKey: 'datastructureIds',
  createEntity: (api, name) => api.createDatastructure({ name }),
})
