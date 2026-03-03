import z from 'zod'

export type Assignment = {
  id: string
  createdAt: string
  modifiedAt: string
  group: { id: string; name: string }
  role: { id: string; name: string }
  scopeType: string
  scope: { id: string; name: string }
}

export type CreateAssignmentData = { groupId: string; roleId: string }

export type UpdateAssignmentData = CreateAssignmentData & { id: string }

export const AssignmentScopedInputSchema = z.object({
  groupId: z.string().trim().min(1, 'common.errors.required'),
  roleId: z.string().trim().min(1, 'common.errors.required'),
})
