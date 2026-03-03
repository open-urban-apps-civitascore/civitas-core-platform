import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput } from '@/types/common'
import { Datasource, DatasourceCreateData, DatasourceUpdateData } from '@/types/datasources'

const key = 'datasources'

export const useGetDatasources = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Datasource[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading datasources.',
  })

export const useGetDatasource = ({ id, isEnabled }: GetItemInput) =>
  useDataQuery<Datasource>({
    id,
    key,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading datasource.',
  })

export const useCreateDatasource = () =>
  useCreateMutation<Datasource, DatasourceCreateData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating datasource',
  })

export const useUpdateDatasource = () =>
  useUpdateMutation<Datasource, DatasourceUpdateData>({
    method: 'PATCH',
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datasource',
  })

export const useDeleteDatasource = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) =>
      apiRequest({
        method: 'DELETE',
        endpoint: `/${key}/${id}`,
        headers: { 'x-api-request': 'true' },
        errorMessage: 'An error occurred while deleting datasource',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}
