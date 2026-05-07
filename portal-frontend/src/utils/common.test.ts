import z from 'zod'

import { enumFromConst, getRequestEndpoint, isFn } from './common'

describe('isFn', () => {
  it('returns true for a function', () => {
    expect(isFn(() => 'result')).toBe(true)
  })

  it('returns false for a string', () => {
    expect(isFn('some-endpoint')).toBe(false)
  })

  it('returns false for undefined', () => {
    expect(isFn(undefined)).toBe(false)
  })
})

describe('getRequestEndpoint', () => {
  it('calls the endpoint function with the given value', () => {
    const endpointFn = (id: number) => `/items/${id}`
    expect(getRequestEndpoint(endpointFn, 42)).toBe('/items/42')
  })

  it('throws when value is undefined', () => {
    const endpointFn = (id: number) => `/items/${id}`
    expect(() => getRequestEndpoint(endpointFn, undefined)).toThrow()
  })

  it('throws when value is null', () => {
    const endpointFn = (id: number | null) => `/items/${id}`
    expect(() => getRequestEndpoint(endpointFn, null)).toThrow()
  })
})

describe('enumFromConst', () => {
  const STATUS = { ACTIVE: 'active', INACTIVE: 'inactive' } as const

  it('produces a Zod enum that validates known values and rejects unknown ones', () => {
    const StatusEnum = enumFromConst(STATUS)

    expect(StatusEnum).toBeInstanceOf(z.ZodEnum)
    expect(StatusEnum.parse('active')).toBe('active')
    expect(StatusEnum.parse('inactive')).toBe('inactive')
    expect(() => StatusEnum.parse('pending')).toThrow()
    expect(() => StatusEnum.parse('')).toThrow()
  })
})
