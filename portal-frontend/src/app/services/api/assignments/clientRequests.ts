import { useQueryClient } from '@tanstack/react-query'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { AssignmentScope } from '@/types/assignments'
import { GetListInput } from '@/types/common'

export type AssignmentSummary = {
  id: string
  group: { id: string; name: string }
  role: { id: string; name: string; roleType: string; description: string; readonly: boolean }
  scopeType: string | null
  scope: { id: string; name: string } | null
}

export type CreateAssignmentData = {
  groupId: string
  roleId: string
  scopeType?: AssignmentScope
}

const key = 'assignments'

export const useGetAssignments = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<AssignmentSummary[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading assignments.',
  })

export const useCreateAssignment = () =>
  useCreateMutation<AssignmentSummary, CreateAssignmentData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating the assignment.',
  })

export const useDeleteAssignment = () => {
  const queryClient = useQueryClient()

  return async (assignmentId: string): Promise<void> => {
    await apiRequest({
      method: 'DELETE',
      endpoint: `/assignments/${assignmentId}`,
      headers: { 'x-api-request': 'true' },
      errorMessage: 'An error occurred while deleting the assignment.',
    })
    await queryClient.invalidateQueries({ queryKey: [key] })
  }
}
