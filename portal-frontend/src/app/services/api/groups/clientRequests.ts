import { useCreateMutation } from '@/hooks/use-create-mutation copy'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput } from '@/types/common'
import { CreateGroupData, Group, PatchGroupData, UpdateGroupData } from '@/types/groups'

const key = 'groups'

export const useGetGroups = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Group[]>({
    key,
    params,
    isEnabled,
    errorMessage: 'An error occurred while fetching groups.',
  })

export const useGetGroup = ({ id, isEnabled }: GetItemInput) =>
  useDataQuery<Group>({
    id,
    key,
    isEnabled,
    errorMessage: 'An error occurred while fetching groups.',
  })

export const useCreateGroup = () => {
  return useCreateMutation<Group, CreateGroupData>({
    key,
    errorMessage: 'An error occurred while creating the group.',
  })
}

export const useUpdateGroup = () =>
  useUpdateMutation<Group, UpdateGroupData>({
    method: 'PUT',
    key,
    errorMessage: 'An error occurred while updating the group.',
  })

export const usePatchGroup = () =>
  useUpdateMutation<Group, PatchGroupData>({
    method: 'PATCH',
    key,
    errorMessage: 'An error occurred while updating the group.',
  })
