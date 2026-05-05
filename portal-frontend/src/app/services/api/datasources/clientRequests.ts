import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput, WithId } from '@/types/common'
import { Datasource, DatasourceCreateData, DatasourcePatchData, DatasourcePutData } from '@/types/datasources'

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
  useUpdateMutation<Datasource, DatasourcePatchData>({
    method: 'PATCH',
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datasource',
  })

export const useUpdateDatasourceReleased = () =>
  useUpdateMutation<Datasource, DatasourcePutData>({
    method: 'PUT',
    key,
    endpoint: ({ id }) => `/datasources/${id}/released/meta`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datasource',
  })

export const useReleaseDatasource = () =>
  useCreateMutation<Datasource, WithId>({
    key,
    endpoint: ({ id }) => `/datasources/${id}/release`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while releasing datasource',
  })

export const useUnreleaseDatasource = () =>
  useCreateMutation<Datasource, WithId>({
    key,
    endpoint: ({ id }) => `/datasources/${id}/unrelease`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while unreleasing datasource',
  })

export const useDeleteDatasource = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting datasource',
  })
