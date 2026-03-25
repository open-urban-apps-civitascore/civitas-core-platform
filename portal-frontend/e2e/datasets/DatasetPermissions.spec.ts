/**
 * E2E tests for dataset permission gating.
 * Tests table view (create button, delete menu) and detail view (edit button)
 * with both TENANT-scoped and entity-scoped permissions.
 */
import { registerEntityPermissionTests } from '../data-entities/dataEntityPermissionTests'

registerEntityPermissionTests({
  name: 'Dataset',
  path: 'datasets',
  tableTestId: 'datasetsTable',
  createButtonTestId: 'addDatasetButton',
  permissionPrefix: 'DATASET',
  scopeType: 'DATASET',
  prefix: 'dset',
  resourceKey: 'datasetIds',
  createEntity: (api, name) => api.createDataset({ name }),
})
