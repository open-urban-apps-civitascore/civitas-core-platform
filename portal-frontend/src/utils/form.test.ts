import { zodResolver } from '@hookform/resolvers/zod'
import { act, renderHook } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { findErrorPaths, pickDirtyValues, triggerKeepingServerErrors } from './form'

describe('pickDirtyValues', () => {
  it('picks top-level dirty fields and omits non-dirty fields', () => {
    const values = { name: 'foo', description: 'bar', count: 42 }
    const dirtyFields = { name: true, count: true }

    const result = pickDirtyValues(values, dirtyFields)

    expect(result).toEqual({ name: 'foo', count: 42 })
  })

  it('recursively picks dirty nested fields', () => {
    const values = { meta: { title: 'hello', subtitle: 'world' }, status: 'active' }
    const dirtyFields = { meta: { title: true } }

    const result = pickDirtyValues(values, dirtyFields)

    expect(result).toEqual({ meta: { title: 'hello' } })
  })

  it('does not include a nested key when no nested fields are dirty', () => {
    const values = { meta: { title: 'hello', subtitle: 'world' } }
    const dirtyFields = { meta: { title: false } }

    const result = pickDirtyValues(values, dirtyFields)

    expect(result).toEqual({})
  })

  it('picks dirty fields at top level and nested levels and ignores dirty fields with false value', () => {
    const values = { a: 'picked', b: 'ignored', c: { x: 'nested', y: 'nested2' } }
    const dirtyFields = { a: true, b: false, c: { x: true, y: false } }

    const result = pickDirtyValues(values, dirtyFields)

    expect(result).toEqual({ a: 'picked', c: { x: 'nested' } })
  })
})

describe('findErrorPaths', () => {
  it('finds the paths of the errors of one type', () => {
    const errors = {
      baseUrl: { type: 'server', message: 'rejected' },
      authentication: { type: { type: 'custom', message: 'required' } },
      apiCalls: [undefined, { headers: [{ name: { type: 'server', message: 'rejected', ref: { name: 'x' } } }] }],
    }

    expect(findErrorPaths(errors, 'server')).toEqual(['baseUrl', 'apiCalls.1.headers.0.name'])
  })

  it('finds no paths without errors', () => {
    expect(findErrorPaths(undefined, 'server')).toEqual([])
  })
})

describe('triggerKeepingServerErrors', () => {
  const schema = z.object({ configuration: z.object({ name: z.string().min(1, 'required'), path: z.string() }) })

  it('keeps an error of the server where the schema has none and shows the error of the schema', async () => {
    const { result } = renderHook(() =>
      useForm({ resolver: zodResolver(schema), defaultValues: { configuration: { name: '', path: '/stations' } } }),
    )
    act(() => {
      result.current.setError('configuration.name', { type: 'server', message: 'rejected' })
      result.current.setError('configuration.path', { type: 'server', message: 'rejected' })
    })

    await act(async () => {
      await triggerKeepingServerErrors(result.current)
    })

    expect(result.current.getFieldState('configuration.name').error).toMatchObject({ message: 'required' })
    expect(result.current.getFieldState('configuration.path').error).toMatchObject({
      type: 'server',
      message: 'rejected',
    })
  })
})
