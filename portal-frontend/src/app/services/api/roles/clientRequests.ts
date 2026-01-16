import { useCreateMutation } from '@/hooks/use-create-mutation copy'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput } from '@/types/common'
import { CreateRoleData, RoleResponse, UpdateRoleData } from '@/types/roles'

const key = 'roles'

export const useGetRoles = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<RoleResponse[]>({ key, params, isEnabled, errorMessage: 'An error occurred while loading roles.' })

export const useGetRole = ({ id, isEnabled }: GetItemInput) =>
  useDataQuery<RoleResponse>({ id, key, isEnabled, errorMessage: 'An error occurred while loading role.' })

export const useCreateRole = () =>
  useCreateMutation<RoleResponse, CreateRoleData>({ key, errorMessage: 'An error occurred while creating the role.' })

export const useUpdateRole = () =>
  useUpdateMutation<RoleResponse, UpdateRoleData>({
    key,
    method: 'PUT',
    errorMessage: 'An error occurred while updating the role.',
  })

export const useDeleteRole = (id: string) =>
  useDeleteMutation({ id, key, errorMessage: 'An error occurred while creating the role.' })
