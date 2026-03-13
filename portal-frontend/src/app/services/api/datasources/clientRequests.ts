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

export const useUpdateDatasourcePublished = () =>
  useUpdateMutation<Datasource, DatasourcePutData>({
    method: 'PUT',
    key,
    endpoint: ({ id }) => `/datasources/${id}/published/meta`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datasource',
  })

export const usePublishDatasource = () =>
  useCreateMutation<Datasource, WithId>({
    key,
    endpoint: ({ id }) => `/datasources/${id}/publish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datasource',
  })

export const useUnpublishDatasource = () =>
  useCreateMutation<Datasource, WithId>({
    key,
    endpoint: ({ id }) => `/datasources/${id}/unpublish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datasource',
  })

export const useDeleteDatasource = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting datasource',
  })
