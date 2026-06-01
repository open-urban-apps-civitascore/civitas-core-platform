import { pickDirtyValues } from './form'

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
