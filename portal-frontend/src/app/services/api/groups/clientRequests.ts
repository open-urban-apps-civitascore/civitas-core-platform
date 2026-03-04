import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput } from '@/types/common'
import { CreateGroupData, Group, UpdateGroupData } from '@/types/groups'

const key = 'groups'

export const useGetGroups = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Group[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching groups.',
  })

export const useGetGroup = ({ id, isEnabled }: GetItemInput) =>
  useDataQuery<Group>({
    id,
    key,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching groups.',
  })

export const useCreateGroup = () => {
  return useCreateMutation<Group, CreateGroupData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating the group.',
  })
}

export const useUpdateGroup = () =>
  useUpdateMutation<Group, UpdateGroupData>({
    key,
    method: 'PUT',
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating the group.',
  })

export const usePatchGroup = () =>
  useUpdateMutation<Group, UpdateGroupData>({
    key,
    method: 'PATCH',
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating the group.',
  })

export const useDeleteGroup = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating the group.',
  })
