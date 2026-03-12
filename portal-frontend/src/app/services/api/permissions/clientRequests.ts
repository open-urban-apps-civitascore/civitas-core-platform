import { useDataQuery } from '@/hooks/use-data-query'
import { GetListInput } from '@/types/common'
import { Permission } from '@/types/permissions'

const key = 'permissions'

export const useGetPermissions = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Permission[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading permissions data.',
  })
