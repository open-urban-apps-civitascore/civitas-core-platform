import { z } from 'zod'

import { Item } from './common'

export type Group = {
  id: string
  title: string
  description: string
  roles: string[]
  users: { id: string; assignedAt: string }[]
  contact: { id: string; displayName: string } | null
  parent: string | null
  subgroups: Group[]
  dataspace: Item | null
}

export type UserGroupsListData = {
  id: string
  title: string
  description: string
  roles: string[]
  memberSince: string
  contact: { id: string; displayName: string } | null
}

export type UpdateGroupData = Group
export type CreateGroupData = Omit<Group, 'id'>

export interface GroupTabProps {
  groupData: Group
  onCancel?: () => void
}

export const GroupBaseFormDataSchema = z.object({
  id: z.string(),
  title: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
  contact: z.string(),
})

export type GroupBaseFormData = z.infer<typeof GroupBaseFormDataSchema>
