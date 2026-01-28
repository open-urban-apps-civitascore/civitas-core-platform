import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { BaseDatasource, CreateDatasourceData, UpdateDatasourceData } from '@/types/datasources'

const key = 'datasources'

export const useGetDatasources = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<BaseDatasource[]>({
    key,
    params,
    isEnabled,
    errorMessage: 'An error occurred while loading datasources.',
  })

export const useCreateDatasource = () =>
  useCreateMutation<BaseDatasource, CreateDatasourceData>({
    key,
    errorMessage: 'An error occurred while creating datasource',
  })

export const useUpdateDatasource = () =>
  useUpdateMutation<BaseDatasource, UpdateDatasourceData>({
    method: 'PATCH',
    key,
    errorMessage: 'An error occurred while updating datasource',
  })
