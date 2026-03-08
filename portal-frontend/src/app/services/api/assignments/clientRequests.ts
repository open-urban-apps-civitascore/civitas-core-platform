import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { Assignment, CreateAssignmentData } from '@/types/assignments'
import { GetListInput } from '@/types/common'

const key = 'assignments'

export const useGetAssignments = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Assignment[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading assignments.',
  })

export const useCreateAssignment = () =>
  useCreateMutation<Assignment, CreateAssignmentData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating the assignment.',
  })

export const useDeleteAssignment = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting the assignment.',
  })
