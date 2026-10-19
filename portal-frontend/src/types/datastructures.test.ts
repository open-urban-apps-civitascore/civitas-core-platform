import {
  DatastructureCreateDataSchema,
  DatastructureCreateFormSchema,
  DatastructureFormAvailableSchema,
  DatastructureFormDraftSchema,
} from './datastructures'

const baseDraft = {
  id: 'ds-1',
  name: 'A data structure',
  dataStructureStatus: 'DRAFT',
  dataStructureVersionIds: [],
}

describe.each([
  ['DatastructureCreateFormSchema', DatastructureCreateFormSchema, { name: 'A data structure' }],
  ['DatastructureCreateDataSchema', DatastructureCreateDataSchema, { name: 'A data structure' }],
  ['DatastructureFormDraftSchema', DatastructureFormDraftSchema, baseDraft],
  ['DatastructureFormAvailableSchema', DatastructureFormAvailableSchema, baseDraft],
])('%s description validation', (_name, schema, base) => {
  it('rejects a missing description', () => {
    const result = schema.safeParse(base)
    expect(result.success).toBe(false)
  })

  it('rejects a blank description', () => {
    const result = schema.safeParse({ ...base, description: '   ' })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['description'], message: 'common.errors.descriptionRequired' }),
      )
    }
  })

  it('accepts a non-blank description', () => {
    const result = schema.safeParse({ ...base, description: 'A description' })
    expect(result.success).toBe(true)
  })
})
