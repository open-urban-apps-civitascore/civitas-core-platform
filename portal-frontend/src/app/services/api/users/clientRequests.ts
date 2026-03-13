import { useQuery } from '@tanstack/react-query'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { CurrentUser } from '@/types/currentUser'
import { CreateUserData, UpdateUserData, User } from '@/types/users'

const key = 'users'

export const useGetUsers = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<User[]>({
    key,
    params,
    errorMessage: 'An error occurred while fetching users data.',
    isEnabled,
    headers: { 'x-api-request': 'true' },
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
    method: 'PATCH',
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating the user.',
  })

// Uses raw useQuery instead of useDataQuery — useDataQuery doesn't support
// custom staleTime or non-standard endpoints (/users/me vs /{key}/{id}).
// 5 min staleTime: permissions rarely change mid-session.
export const useGetCurrentUser = () =>
  useQuery<CurrentUser>({
    queryKey: ['currentUser'],
    queryFn: () =>
      apiRequest<CurrentUser>({
        endpoint: '/users/me',
        method: 'GET',
        headers: { 'x-api-request': 'true' },
      }).then(r => r.data),
    staleTime: 5 * 60 * 1000,
  })
