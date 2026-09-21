import { useDataQuery } from '@/hooks/use-data-query'

/**
 * The data structure a FROST sink port publishes.
 *
 * The structure is the contract of the sink: what a record must carry for the port to write it. It
 * belongs to the platform, it is the same for every Tenant and nobody edits it, so it is read here
 * rather than selected like a modelled Data structure. The document is a JSON Schema model, the
 * same artifact a Data structure version carries.
 */
export const useGetFrostPortStructure = ({ port, isEnabled }: { port?: string; isEnabled?: boolean }) =>
  useDataQuery<Record<string, unknown>>({
    id: 'structure',
    key: `frost-sink-ports/${port ?? ''}`,
    isEnabled: isEnabled && Boolean(port),
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching the structure of the FROST sink port.',
  })
