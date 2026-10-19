import { describe, expect, it, vi } from 'vitest'

const useDataQuery = vi.fn()
vi.mock('@/hooks/use-data-query', () => ({ useDataQuery: (options: unknown) => useDataQuery(options) }))

const { useGetPublishedStructure, useGetPublishedStructures } = await import('./clientRequests')

describe('the published structure requests', () => {
  it('reads the list and one document from separate cache entries', () => {
    useGetPublishedStructures({ isEnabled: true })
    const list = useDataQuery.mock.calls.at(-1)![0]

    // No structure selected yet: the document request has no id either.
    useGetPublishedStructure({ structureKey: undefined, isEnabled: false })
    const document = useDataQuery.mock.calls.at(-1)![0]

    // The cache entry is [queryKey ?? key, id]. Sharing it would let the document request answer
    // with the list — and a list holds no classes, so the import would silently load nothing.
    expect(list.queryKey ?? list.key).not.toBe(document.queryKey ?? document.key)
  })

  it('asks for a document only once a structure is selected', () => {
    useGetPublishedStructure({ structureKey: undefined, isEnabled: true })
    expect(useDataQuery.mock.calls.at(-1)![0].isEnabled).toBe(false)

    useGetPublishedStructure({ structureKey: 'ThingTree', isEnabled: true })
    const enabled = useDataQuery.mock.calls.at(-1)![0]
    expect(enabled.isEnabled).toBe(true)
    expect(enabled.id).toBe('ThingTree')
  })
})
