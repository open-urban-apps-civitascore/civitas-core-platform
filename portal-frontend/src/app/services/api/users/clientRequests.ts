/* eslint-disable @typescript-eslint/naming-convention */
import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { CreateUserData, UpdateUserData, User } from '@/types/users'

const key = 'users'

export const useGetUsers = ({ params, isEnabled, queryKey }: GetListInput = {}) => {
  return useDataQuery<User[]>({
    key,
    queryKey,
    params,
    errorMessage: 'An error occurred while fetching users data.',
    isEnabled,
    headers: { 'x-api-request': 'true' },
  })
}

export const useCreateUser = () =>
  useCreateMutation<User, CreateUserData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating the user.',
  })

export const useUpdateUser = () =>
  useUpdateMutation<User, UpdateUserData>({
    key,
    method: 'PATCH',
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating the user.',
  })
