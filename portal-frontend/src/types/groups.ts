import { z } from 'zod'

import { ItemScheme, WithId } from './common'

export const GroupRoleTypes = z.enum(['system', 'data', 'governance'])

export const GroupRoleScheme = ItemScheme.extend({
  roleType: GroupRoleTypes,
})

export const GroupApiResponseSchema = z.object({
  id: z.string(),
  name: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
  roles: z.array(GroupRoleScheme).nullable(),
  members: z.array(ItemScheme).nullable(),
  contactUser: ItemScheme.nullable(),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type Group = z.infer<typeof GroupApiResponseSchema>

export type UserGroupsListData = Omit<Group, 'members' | 'createdAt' | 'modifiedAt'>

export const GroupApiDataSchema = z.object({
  id: z.string(),
  name: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string().optional(),
  contactUserId: z.string().optional(),
  roleIds: z.array(z.string()).optional(),
  memberIds: z.array(z.string()).optional(),
})

export type GroupApiData = z.infer<typeof GroupApiDataSchema>

export const GroupBaseFormDataSchema = z.object({
  id: z.string(),
  name: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
  contactUser: z.string(),
})

export type GroupBaseFormData = z.infer<typeof GroupBaseFormDataSchema>

export type CreateGroupData = Omit<GroupApiData, 'id'> & { name: string }
export type UpdateGroupData = Partial<CreateGroupData> & WithId
