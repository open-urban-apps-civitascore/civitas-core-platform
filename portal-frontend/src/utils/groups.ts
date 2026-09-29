import { Group, GroupApiData, GroupBaseFormData, UserGroupsListData } from '@/types/groups'

export const mapGroupDetailsData = (groupResponse: Group | null): Group | null =>
  groupResponse
    ? {
        ...groupResponse,
        contactUser: groupResponse.contactUser || { id: '', name: '' },
        assignments: groupResponse.assignments || [],
      }
    : null

export const mapGroupApiToFormData = (groupData: Group): GroupBaseFormData => ({
  id: groupData.id,
  name: groupData.name,
  description: groupData.description || '',
  contactUserId: groupData.contactUser?.id || '',
  members: groupData.members?.map(member => member.id) || [],
  assignments:
    groupData.assignments?.map(a => ({
      groupId: a.group.id,
      roleId: a.role.id,
      scopeType: a.scopeType,
      scopeId: a.scope?.id ?? null,
    })) || [],
})

export const mapGroupFormToApiData = (groupData: GroupBaseFormData): GroupApiData => ({
  id: groupData.id,
  name: groupData.name,
  description: groupData.description || '',
  contactUserId: groupData.contactUserId,
  memberIds: groupData.members,
})

export const mapGroupsApiToListData = (groups: Group[]): UserGroupsListData[] =>
  groups.map(group => ({
    id: group.id,
    name: group.name,
    description: group.description,
    membersCount: group.members?.length || 0,
    contactUser: group.contactUser,
  }))
