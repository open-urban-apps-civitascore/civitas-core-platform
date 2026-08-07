import { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import {
  isDatapoolScopeViolationError,
  isNameConflictError,
  isPermissionsError,
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

describe('isTableNameConflictError', () => {
  it('returns true for a 409 error naming the tableName field', () => {
    const error = getError(409, "DataSink with configuration.tableName 'roads' and dataSetId 'ds-1' already exists")
    expect(isTableNameConflictError(error)).toBe(true)
  })

  it('returns false for a 409 error about another field', () => {
    const error = getError(409, 'Group with name "Local Data Consumers" already exists')
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for non-409 status codes', () => {
    const error = getError(400, "DataSink with configuration.tableName 'roads' already exists")
    expect(isTableNameConflictError(error)).toBe(false)
  })

  it('returns false for non-axios errors', () => {
    expect(isTableNameConflictError(new Error('plain error'))).toBe(false)
  })
})
