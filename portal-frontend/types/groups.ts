import { z } from 'zod'

export type GroupResponse = {
  id: string
  title: string
  description: string | null
  roles: string[]
  users: string[]
  contact: { id: string; displayName: string }
  subgroups: GroupResponse[] | null
}

export const GroupSchema = z.object({
  id: z.string(),
  title: z.string().min(2).or(z.literal('')),
  description: z.string().min(2).or(z.literal('')).nullable(),
  roles: z.array(z.string()),
  users: z.array(z.string()),
  contact: z.string(),
  get subgroups() {
    return z.array(GroupSchema)
  },
})

export type GroupData = z.infer<typeof GroupSchema>
export type CreateGroupData = Omit<GroupData, 'id'>
