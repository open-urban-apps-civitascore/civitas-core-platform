import { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'

import { isNameConflictError, isPermissionsError } from './errors'

const getError = (status: number, detail?: string) => {
  return {
    status: status,
    response: {
      status: status,
      data: {
        detail,
      },
    },
  } as AxiosError
}

describe('isNameConflictError', () => {
  it('returns true for a 409 conflict error with name conflict message', () => {
    const error1 = getError(409, 'Datasource with name "Test" already exists')
    expect(isNameConflictError(error1)).toBe(true)
    const error2 = getError(409, 'Role with name "Admin" already exists')
    expect(isNameConflictError(error2)).toBe(true)
  })

  it('returns false for non-409 status codes', () => {
    const error = {
      status: 400,
      response: {
        status: 400,
        data: {
          detail: 'Datasource with name "Test" already exists',
        },
      },
    } as AxiosError

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when response is missing', () => {
    const error = getError(409)

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when detail is missing "with name"', () => {
    const error = getError(409, 'Datasource "Test" already exists')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when detail is missing "already exists"', () => {
    const error = getError(409, 'Datasource with name "Test" is in use')

    expect(isNameConflictError(error)).toBe(false)
  })

  it('returns false when both required strings are missing', () => {
    const error = getError(409, 'Datasource validation failed')
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
