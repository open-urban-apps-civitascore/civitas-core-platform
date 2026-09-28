import { describe, expect, it } from 'vitest'

import { mockApiError } from '@/__mocks__/errors/apiError.mock'

import {
  isDatapoolScopeViolationError,
  isDatastructureNotAvailableError,
  isNameConflictError,
  isNotDraftError,
  isPermissionsError,
  isPipelineClosureInvalidError,
  isResourceInUseError,
  isSagaInFlightError,
  isTableNameConflictError,
  isUnconfirmedDataLossError,
} from './errors'

describe('isNameConflictError', () => {
  it('returns true for a 409 conflict error with name conflict message', () => {
    const error1 = mockApiError(409, 'Group with name "Local Data Consumers" already exists')
    expect(isNameConflictError(error1)).toBe(true)
    const error2 = mockApiError(409, 'Role with name "Admin" already exists')
    expect(isNameConflictError(error2)).toBe(true)
  })

  it('returns false for non-409 status codes', () => {
    const error = mockApiError(400, 'Role with name "Admin" already exists')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when response is missing', () => {
    const error = mockApiError(409)

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when detail is missing "with name"', () => {
    const error = mockApiError(409, 'Group "Local Data Consumers" already exists')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when detail is missing "already exists"', () => {
    const error = mockApiError(409, 'Group with name "Local Data Consumers" is in use')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when both required strings are missing', () => {
    const error = mockApiError(409, 'Role validation failed')
    expect(isNameConflictError(error)).toBe(false)
  })
})

describe('isPermissionsError', () => {
  it('returns true for a 403 forbidden error', () => {
    const error = mockApiError(403, 'User does not have permission')
    expect(isPermissionsError(error)).toBe(true)
  })

  it('returns false for non-403 status codes', () => {
    const error = mockApiError(401, 'Unauthorized')
    expect(isPermissionsError(error)).toBe(false)
  })
})

describe('isDatapoolScopeViolationError', () => {
  it('returns true for a 422 error carrying the DATASOURCE_SCOPE_VIOLATION type', () => {
    const error = mockApiError(
      422,
      'DataSource "My DS" is not permitted for this datapool',
      'urn:civitas:error:DATASOURCE_SCOPE_VIOLATION',
    )
    expect(isDatapoolScopeViolationError(error)).toBe(true)
  })

  it('returns false for a 422 error with a different error type', () => {
    const error = mockApiError(422, 'Some other validation failed', 'urn:civitas:error:SOME_OTHER_ERROR')
    expect(isDatapoolScopeViolationError(error)).toBe(false)
  })

  it('returns false for a 422 error without a type field', () => {
    const error = mockApiError(422, 'Some other validation failed')
    expect(isDatapoolScopeViolationError(error)).toBe(false)
  })

  it('returns false for non-422 status codes', () => {
    const error = mockApiError(400, 'Bad request')
    expect(isDatapoolScopeViolationError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isDatapoolScopeViolationError(new Error('plain error'))).toBe(false)
  })
})

const TABLE_NAME_CONFLICT_ON_CREATE =
  "Another POSTGIS DataSink of this dataset already uses tableName 'shared_table'; they would share one physical table"

const TABLE_NAME_CONFLICT_ON_UPDATE =
  "This DataSink's tableName 'T_ONE' is already used by another POSTGIS DataSink of this dataset; rename it to change this sink"

describe('isTableNameConflictError', () => {
  it('returns true when a new data storage uses a table name that is already taken', () => {
    const error = mockApiError(409, TABLE_NAME_CONFLICT_ON_CREATE, 'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION')
    expect(isTableNameConflictError(error)).toBe(true)
  })

  it('returns true when a saved data storage is renamed to a table name that is already taken', () => {
    const error = mockApiError(409, TABLE_NAME_CONFLICT_ON_UPDATE, 'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION')
    expect(isTableNameConflictError(error)).toBe(true)
  })

  it('returns false when two pipelines have the same name', () => {
    const error = mockApiError(
      409,
      "Pipeline with name 'Test' and datasetId 'ds-1' already exists",
      'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION',
    )
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for a 409 error without a type field', () => {
    const error = mockApiError(409, TABLE_NAME_CONFLICT_ON_CREATE)
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for non-409 status codes', () => {
    const error = mockApiError(400, TABLE_NAME_CONFLICT_ON_CREATE, 'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION')
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isTableNameConflictError(new Error('plain error'))).toBe(false)
  })
})

describe('isNotDraftError', () => {
  it('returns true for a 400 error carrying the DATASET_NOT_EDITABLE type', () => {
    const error = mockApiError(
      400,
      'DataSet must be in DRAFT to modify sub-entities',
      'urn:civitas:error:DATASET_NOT_EDITABLE',
    )
    expect(isNotDraftError(error)).toBe(true)
  })

  it('returns false for a 400 error with a different type', () => {
    const error = mockApiError(400, 'Bad request', 'urn:civitas:error:INVALID_INPUT')
    expect(isNotDraftError(error)).toBe(false)
  })

  it('returns false for a 400 error without a type field', () => {
    const error = mockApiError(400, 'Bad request')
    expect(isNotDraftError(error)).toBe(false)
  })

  it('returns false for non-400 status codes', () => {
    const error = mockApiError(409, 'Conflict', 'urn:civitas:error:DATASET_NOT_EDITABLE')
    expect(isNotDraftError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isNotDraftError(new Error('plain error'))).toBe(false)
  })
})

describe('isDatastructureNotAvailableError', () => {
  it.each([
    'DataStructureVersion must be in AVAILABLE status for an AVAILABLE DataSource',
    'The parent DataStructure must be in AVAILABLE status for an AVAILABLE DataSource',
  ])('returns true for a 400 INVALID_INPUT error: %s', detail => {
    const error = mockApiError(400, detail, 'urn:civitas:error:INVALID_INPUT')
    expect(isDatastructureNotAvailableError(error)).toBe(true)
  })

  it('returns false for a 400 INVALID_INPUT error with a different detail', () => {
    const error = mockApiError(400, 'Bad request', 'urn:civitas:error:INVALID_INPUT')
    expect(isDatastructureNotAvailableError(error)).toBe(false)
  })

  it('returns false for non-400 status codes', () => {
    const error = mockApiError(
      409,
      'DataStructureVersion must be in AVAILABLE status for an AVAILABLE DataSource',
      'urn:civitas:error:INVALID_INPUT',
    )
    expect(isDatastructureNotAvailableError(error)).toBe(false)
  })
})

const SAGA_IN_FLIGHT = 'urn:civitas:error:SAGA_IN_FLIGHT'

describe('isSagaInFlightError', () => {
  it.each([
    'Cannot write while a saga is in-flight: UNRELEASE',
    'Cannot write while a saga is in-flight: DELETE',
    'Cannot release while a saga is in-flight: CREATE',
    'Cannot unrelease while a saga is in-flight: UPDATE',
  ])('returns true for a saga rejection: %s', detail => {
    expect(isSagaInFlightError(mockApiError(409, detail, SAGA_IN_FLIGHT))).toBe(true)
  })

  it('returns false for a 409 error with a different type', () => {
    const error = mockApiError(409, 'Name conflict', 'urn:civitas:error:SOME_OTHER_ERROR')
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for a 409 error without a type field', () => {
    const error = mockApiError(409, 'Conflict')
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for non-409 status codes', () => {
    const error = mockApiError(400, 'Cannot write while a saga is in-flight: UNRELEASE', SAGA_IN_FLIGHT)
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isSagaInFlightError(new Error('plain error'))).toBe(false)
  })
})

const RESOURCE_IN_USE = 'urn:civitas:error:RESOURCE_IN_USE'

describe('isResourceInUseError', () => {
  it('returns true when a style is still referenced by layers', () => {
    const error = mockApiError(409, 'Style is referenced by one or more Layers', RESOURCE_IN_USE)
    expect(isResourceInUseError(error)).toBe(true)
  })

  it('returns true when a data sink is still referenced by layers', () => {
    const error = mockApiError(409, 'DataSink is referenced by one or more Layers', RESOURCE_IN_USE)
    expect(isResourceInUseError(error)).toBe(true)
  })

  it('returns true when a mapping is still referenced by a pipeline', () => {
    const error = mockApiError(
      409,
      'Cannot delete urn:core:mapping:x — still referenced by: urn:core:pipeline:y',
      RESOURCE_IN_USE,
    )
    expect(isResourceInUseError(error)).toBe(true)
  })

  it('returns true when a data source is pinned to a data structure version', () => {
    const error = mockApiError(
      409,
      'Cannot modify DataStructureVersion because a DataSource is pinned to it.',
      RESOURCE_IN_USE,
    )
    expect(isResourceInUseError(error)).toBe(true)
  })

  it('returns false for a missing data-loss confirmation, which shares the type', () => {
    const error = mockApiError(
      409,
      "This change rebuilds the sink's table and discards all stored data; set confirmDataLoss=true to proceed",
      RESOURCE_IN_USE,
    )
    expect(isResourceInUseError(error)).toBe(false)
  })

  it('returns false for a 409 error with a different type', () => {
    const error = mockApiError(409, 'Style is referenced by one or more Layers', SAGA_IN_FLIGHT)
    expect(isResourceInUseError(error)).toBe(false)
  })

  it('returns false for a 409 error without a type field', () => {
    const error = mockApiError(409, 'Style is referenced by one or more Layers')
    expect(isResourceInUseError(error)).toBe(false)
  })

  it('returns false for non-409 status codes', () => {
    const error = mockApiError(400, 'Style is referenced by one or more Layers', RESOURCE_IN_USE)
    expect(isResourceInUseError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isResourceInUseError(new Error('plain error'))).toBe(false)
  })
})

const UNCONFIRMED_DATA_LOSS =
  "This change rebuilds the sink's table and discards all stored data; set confirmDataLoss=true to proceed"

describe('isUnconfirmedDataLossError', () => {
  it('returns true when a destructive sink change was not confirmed', () => {
    const error = mockApiError(409, UNCONFIRMED_DATA_LOSS, RESOURCE_IN_USE)
    expect(isUnconfirmedDataLossError(error)).toBe(true)
  })

  it('returns false when a style is still referenced, which shares the type', () => {
    const error = mockApiError(409, 'Style is referenced by one or more Layers', RESOURCE_IN_USE)
    expect(isUnconfirmedDataLossError(error)).toBe(false)
  })

  it('returns false when a data sink is still referenced, which shares the type', () => {
    const error = mockApiError(409, 'DataSink is referenced by one or more Layers', RESOURCE_IN_USE)
    expect(isUnconfirmedDataLossError(error)).toBe(false)
  })

  it('returns false for a 409 error with a different type', () => {
    const error = mockApiError(409, UNCONFIRMED_DATA_LOSS, SAGA_IN_FLIGHT)
    expect(isUnconfirmedDataLossError(error)).toBe(false)
  })

  it('returns false for a 409 error without a type field', () => {
    const error = mockApiError(409, UNCONFIRMED_DATA_LOSS)
    expect(isUnconfirmedDataLossError(error)).toBe(false)
  })

  it('returns false for non-409 status codes', () => {
    const error = mockApiError(400, UNCONFIRMED_DATA_LOSS, RESOURCE_IN_USE)
    expect(isUnconfirmedDataLossError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isUnconfirmedDataLossError(new Error('plain error'))).toBe(false)
  })
})

const PIPELINE_CLOSURE_INVALID = 'urn:civitas:error:PIPELINE_CLOSURE_INVALID'

describe('isPipelineClosureInvalidError', () => {
  it('returns true for a 422 closure rejection', () => {
    const error = mockApiError(422, 'Pipeline closure validation failed', PIPELINE_CLOSURE_INVALID)
    expect(isPipelineClosureInvalidError(error)).toBe(true)
  })

  it('returns false for a 422 error with a different type', () => {
    const error = mockApiError(422, 'Scope violation', 'urn:civitas:error:DATASOURCE_SCOPE_VIOLATION')
    expect(isPipelineClosureInvalidError(error)).toBe(false)
  })

  it('returns false for another status carrying the same type', () => {
    const error = mockApiError(409, 'Pipeline closure validation failed', PIPELINE_CLOSURE_INVALID)
    expect(isPipelineClosureInvalidError(error)).toBe(false)
  })
})
