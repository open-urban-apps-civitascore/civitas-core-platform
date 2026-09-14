import { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import {
  isDatapoolScopeViolationError,
  isNameConflictError,
  isNotDraftError,
  isPermissionsError,
  isSagaInFlightError,
  isTableNameConflictError,
} from './errors'

const getError = (status: number, detail?: string, type?: string) => {
  return new AxiosError(
    'request failed',
    undefined,
    {
      headers: new AxiosHeaders(),
      method: 'GET',
      url: '/test',
    } as InternalAxiosRequestConfig,
    undefined,
    {
      status,
      statusText: '',
      headers: new AxiosHeaders(),
      config: {
        headers: new AxiosHeaders(),
        method: 'GET',
        url: '/test',
      } as InternalAxiosRequestConfig,
      data: {
        detail,
        type,
      },
    },
  )
}

describe('isNameConflictError', () => {
  it('returns true for a 409 conflict error with name conflict message', () => {
    const error1 = getError(409, 'Group with name "Local Data Consumers" already exists')
    expect(isNameConflictError(error1)).toBe(true)
    const error2 = getError(409, 'Role with name "Admin" already exists')
    expect(isNameConflictError(error2)).toBe(true)
  })

  it('returns false for non-409 status codes', () => {
    const error = getError(400, 'Role with name "Admin" already exists')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when response is missing', () => {
    const error = getError(409)

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when detail is missing "with name"', () => {
    const error = getError(409, 'Group "Local Data Consumers" already exists')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when detail is missing "already exists"', () => {
    const error = getError(409, 'Group with name "Local Data Consumers" is in use')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when both required strings are missing', () => {
    const error = getError(409, 'Role validation failed')
    expect(isNameConflictError(error)).toBe(false)
  })
})

describe('isPermissionsError', () => {
  it('returns true for a 403 forbidden error', () => {
    const error = getError(403, 'User does not have permission')
    expect(isPermissionsError(error)).toBe(true)
  })

  it('returns false for non-403 status codes', () => {
    const error = getError(401, 'Unauthorized')
    expect(isPermissionsError(error)).toBe(false)
  })
})

describe('isDatapoolScopeViolationError', () => {
  it('returns true for a 422 error carrying the DATASOURCE_SCOPE_VIOLATION type', () => {
    const error = getError(
      422,
      'DataSource "My DS" is not permitted for this datapool',
      'urn:civitas:error:DATASOURCE_SCOPE_VIOLATION',
    )
    expect(isDatapoolScopeViolationError(error)).toBe(true)
  })

  it('returns false for a 422 error with a different error type', () => {
    const error = getError(422, 'Some other validation failed', 'urn:civitas:error:SOME_OTHER_ERROR')
    expect(isDatapoolScopeViolationError(error)).toBe(false)
  })

  it('returns false for a 422 error without a type field', () => {
    const error = getError(422, 'Some other validation failed')
    expect(isDatapoolScopeViolationError(error)).toBe(false)
  })

  it('returns false for non-422 status codes', () => {
    const error = getError(400, 'Bad request')
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
    const error = getError(409, TABLE_NAME_CONFLICT_ON_CREATE, 'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION')
    expect(isTableNameConflictError(error)).toBe(true)
  })

  it('returns true when a saved data storage is renamed to a table name that is already taken', () => {
    const error = getError(409, TABLE_NAME_CONFLICT_ON_UPDATE, 'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION')
    expect(isTableNameConflictError(error)).toBe(true)
  })

  it('returns false when two pipelines have the same name', () => {
    const error = getError(
      409,
      "Pipeline with name 'Test' and datasetId 'ds-1' already exists",
      'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION',
    )
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for a 409 error without a type field', () => {
    const error = getError(409, TABLE_NAME_CONFLICT_ON_CREATE)
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for non-409 status codes', () => {
    const error = getError(400, TABLE_NAME_CONFLICT_ON_CREATE, 'urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION')
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isTableNameConflictError(new Error('plain error'))).toBe(false)
  })
})

describe('isNotDraftError', () => {
  it('returns true for a 400 error carrying the DATASET_NOT_EDITABLE type', () => {
    const error = getError(
      400,
      'DataSet must be in DRAFT to modify sub-entities',
      'urn:civitas:error:DATASET_NOT_EDITABLE',
    )
    expect(isNotDraftError(error)).toBe(true)
  })

  it('returns false for a 400 error with a different type', () => {
    const error = getError(400, 'Bad request', 'urn:civitas:error:INVALID_INPUT')
    expect(isNotDraftError(error)).toBe(false)
  })

  it('returns false for a 400 error without a type field', () => {
    const error = getError(400, 'Bad request')
    expect(isNotDraftError(error)).toBe(false)
  })

  it('returns false for non-400 status codes', () => {
    const error = getError(409, 'Conflict', 'urn:civitas:error:DATASET_NOT_EDITABLE')
    expect(isNotDraftError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isNotDraftError(new Error('plain error'))).toBe(false)
  })
})

describe('isSagaInFlightError', () => {
  it('returns true for a 409 error carrying the RESOURCE_IN_USE type and the saga marker', () => {
    const error = getError(
      409,
      'Cannot write while a saga is in-flight: UNRELEASE',
      'urn:civitas:error:RESOURCE_IN_USE',
    )
    expect(isSagaInFlightError(error)).toBe(true)
  })

  it('returns true for a saga rejection raised outside the sub-entity guard', () => {
    const error = getError(409, 'Cannot release while a saga is in-flight: CREATE', 'urn:civitas:error:RESOURCE_IN_USE')
    expect(isSagaInFlightError(error)).toBe(true)
  })

  it('returns false for a 409 RESOURCE_IN_USE raised by a still-referenced style', () => {
    const error = getError(409, 'Style is referenced by one or more Layers', 'urn:civitas:error:RESOURCE_IN_USE')
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for a 409 RESOURCE_IN_USE raised by a still-referenced data sink', () => {
    const error = getError(409, 'DataSink is referenced by one or more Layers', 'urn:civitas:error:RESOURCE_IN_USE')
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for a 409 error with a different type', () => {
    const error = getError(409, 'Name conflict', 'urn:civitas:error:SOME_OTHER_ERROR')
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for a 409 error without a type field', () => {
    const error = getError(409, 'Conflict')
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for non-409 status codes', () => {
    const error = getError(400, 'Bad request', 'urn:civitas:error:RESOURCE_IN_USE')
    expect(isSagaInFlightError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isSagaInFlightError(new Error('plain error'))).toBe(false)
  })
})
