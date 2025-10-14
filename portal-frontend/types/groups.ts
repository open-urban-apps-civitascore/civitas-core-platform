import { z } from 'zod'

export type GroupResponse = {
  id: string
  title: string
  description: string
  roles: string[]
  users: string[]
  contact: { id: string; displayName: string } | null
  subgroups: GroupResponse[]
}

export const GroupSchema = z.object({
  id: z.string(),
  title: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string().min(10, {
    message: 'common.errors.atLeast10',
  }),
  roles: z.array(z.string()),
  users: z.array(z.string()),
  contact: z
    .object({
      id: z.string(),
      displayName: z.string(),
    })
    .nullable(),
  get subgroups() {
    return z.array(GroupSchema)
  },
})

export type GroupData = z.infer<typeof GroupSchema>
export type UpdateGroupData = GroupData
export type CreateGroupData = Omit<GroupData, 'id'>
