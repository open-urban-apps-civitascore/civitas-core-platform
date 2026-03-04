import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput } from '@/types/common'
import { CreateRoleData, Role, UpdateRoleData } from '@/types/roles'

const key = 'roles'

export const useGetRoles = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Role[]>({ key, params, isEnabled, errorMessage: 'An error occurred while loading roles.' })

export const useGetRole = ({ id, isEnabled }: GetItemInput) =>
  useDataQuery<Role>({ id, key, isEnabled, errorMessage: 'An error occurred while loading role.' })

export const useCreateRole = () =>
  useCreateMutation<Role, CreateRoleData>({ key, errorMessage: 'An error occurred while creating the role.' })

export const useUpdateRole = () =>
  useUpdateMutation<Role, UpdateRoleData>({
    key,
    method: 'PUT',
    errorMessage: 'An error occurred while updating the role.',
  })

export const useDeleteRole = () =>
  useDeleteMutation({ key, errorMessage: 'An error occurred while creating the role.' })
