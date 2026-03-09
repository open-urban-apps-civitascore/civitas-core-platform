import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput } from '@/types/common'
import { CreateRoleData, Role, UpdateRoleData } from '@/types/roles'

const key = 'roles'

export const useGetRoles = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Role[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading roles.',
  })

export const useGetRole = ({ id, isEnabled }: GetItemInput) =>
  useDataQuery<Role>({
    id,
    key,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading role.',
  })

export const useCreateRole = () =>
  useCreateMutation<Role, CreateRoleData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating the role.',
  })

export const useUpdateRole = () =>
  useUpdateMutation<Role, UpdateRoleData>({
    key,
    method: 'PUT',
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating the role.',
  })

export const useDeleteRole = () =>
  useDeleteMutation<Role>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting the role.',
  })
