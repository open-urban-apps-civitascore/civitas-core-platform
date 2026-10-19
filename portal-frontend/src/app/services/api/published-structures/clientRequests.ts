import { useDataQuery } from '@/hooks/use-data-query'

/** One structure a sink publishes. */
export interface PublishedStructureSummary {
  /** How the structure is addressed. */
  key: string
  name: string
  /** The version the registry assigned, which an import pins. Absent until it was published. */
  urn?: string
}

/** A sink and the structures it publishes — the two levels of the "Load standard" menu. */
export interface PublishingSink {
  sink: string
  structures: PublishedStructureSummary[]
}

/**
 * The structures the sinks of the platform publish.
 *
 * They belong to the platform, not to a Tenant: every Tenant sees the same list and nobody edits
 * it, so the request carries no Dataset and no Tenant. A sink that begins to publish structures
 * appears here without a change in the editor.
 */
export const useGetPublishedStructures = ({ isEnabled }: { isEnabled?: boolean } = {}) =>
  useDataQuery<PublishingSink[]>({
    key: 'published-structures',
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching the published data structures.',
  })

/** The model document of one published structure: a JSON Schema, like a Data structure version. */
export const useGetPublishedStructure = ({ structureKey, isEnabled }: { structureKey?: string; isEnabled?: boolean }) =>
  useDataQuery<Record<string, unknown>>({
    id: structureKey,
    key: 'published-structures',
    // A cache entry of its own. Both requests read the same path, so without this the two would
    // share the entry `['published-structures', undefined]` while no structure is selected, and
    // the document query would answer with the list.
    queryKey: 'published-structure',
    isEnabled: isEnabled && Boolean(structureKey),
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching the published data structure.',
  })
