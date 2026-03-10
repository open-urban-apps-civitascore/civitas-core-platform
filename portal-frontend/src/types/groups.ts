import { z } from 'zod'

import { AssignmentApiResponseSchema } from './assignments'
import { ItemSchema, WithId } from './common'

export type GroupTab = 'info' | 'roles' | 'users'

export const GroupRoleTypes = z.enum(['SYSTEM', 'DATA', 'GOVERNANCE'])

export const GroupRoleScheme = ItemSchema.extend({
  roleType: GroupRoleTypes,
})

export const GroupApiResponseSchema = z.object({
  id: z.string(),
  name: z.string().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string(),
  roles: z.array(GroupRoleScheme).nullable(),
  members: z.array(ItemSchema).nullable(),
  contactUser: ItemSchema.nullable(),
  parentGroup: ItemSchema.nullable().optional(),
  childGroups: z.array(ItemSchema).nullable().optional(),
  assignments: z.array(AssignmentApiResponseSchema).nullable(),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type Group = z.infer<typeof GroupApiResponseSchema>

export type UserGroupsListData = Pick<Group, 'id' | 'name' | 'description' | 'contactUser'> & {
  membersCount: number
}

export const GroupApiDataSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string().trim().optional(),
  contactUserId: z.string().optional(),
  roleIds: z.array(z.string()).optional(),
  memberIds: z.array(z.string()).optional(),
})

export type GroupApiData = z.infer<typeof GroupApiDataSchema>

export const GroupBaseFormDataSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(2, {
    message: 'common.errors.atLeast2',
  }),
  description: z.string().trim(),
  contactUserId: z.string(),
  members: z.array(z.string()),
})

export type GroupBaseFormData = z.infer<typeof GroupBaseFormDataSchema>

export type CreateGroupData = Omit<GroupApiData, 'id'> & { name: string }
export type UpdateGroupData = Partial<CreateGroupData> & WithId
