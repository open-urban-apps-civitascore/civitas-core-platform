import { useDataQuery } from '@/hooks/use-data-query'
import { GetListInput } from '@/types/common'
import { Datasink } from '@/types/datasinks'

const key = 'datasinks'

export const useGetDatasinks = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Datasink[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading datasinks.',
  })
