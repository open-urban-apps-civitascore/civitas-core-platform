/* eslint-disable @typescript-eslint/naming-convention */
import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { Authority, CreateUserData, UpdateUserData, User } from '@/types/users'

const key = 'users'

export const useGetUsers = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<User[]>({
    key,
    params,
    errorMessage: 'An error occurred while fetching users data.',
    isEnabled,
    headers: { 'x-api-request': 'true' },
  })

// TODO: This query will be removed and it's relating UI-Elements adjusted since the implementation of authorities is not part of v2
export const useGetAuthorities = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Authority[]>({
    key: 'authorities',
    params,
    errorMessage: 'An error occurred while fetching authorities data.',
    isEnabled,
  })

export const useCreateUser = () =>
  useCreateMutation<User, CreateUserData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating the user.',
  })

export const useUpdateUser = () =>
  useUpdateMutation<User, UpdateUserData>({
    key,
    method: 'PUT',
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating the user.',
  })
