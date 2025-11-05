import { z } from 'zod'

export type Group = {
  id: string
  title: string
  description: string
  roles: string[]
  users: { id: string; assignedAt: string }[]
  contact: string | null
  parent: string | null
  subgroups: Group[]
}

export type UpdateGroupData = Group
export type CreateGroupData = Omit<Group, 'id'>

export interface GroupTabProps {
  groupData: Group
  onCancel?: () => void
}

export const GroupBaseInfoSchema = z.object({
  id: z.string(),
  title: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
  contact: z.string().nullable(),
})

export type GroupBaseInfo = z.infer<typeof GroupBaseInfoSchema>
