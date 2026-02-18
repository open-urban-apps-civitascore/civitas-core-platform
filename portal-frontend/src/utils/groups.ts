import { Group, GroupBaseFormData, UserGroupsListData } from '@/types/groups'

export const mapGroupDetailsData = (groupResponse: Group | null): Group | null =>
  groupResponse
    ? {
        ...groupResponse,
        contactUser: groupResponse.contactUser || { id: '', name: '' },
        roles: groupResponse.roles || [],
      }
    : null

export const mapGroupApiToFormData = (groupData: Group): GroupBaseFormData => ({
  id: groupData.id,
  name: groupData.name,
  description: groupData.description || '',
  contactUserId: groupData.contactUser?.id || '',
})

export const mapGroupsApiToListData = (groups: Group[]): UserGroupsListData[] =>
  groups.map(group => ({
    id: group.id,
    name: group.name,
    description: group.description,
    membersCount: group.members?.length || 0,
    contactUser: group.contactUser,
  }))

// TODO: subgroups have been excluded from v::2, so the implementation of subgroups has been commented out
// export const flattenGroups = (groups: Group[]): Group[] => {
//   const result: Group[] = []

//   const processGroup = (group: Group) => {
//     result.push({ ...group, subgroups: [] })
//     group.subgroups?.forEach(processGroup)
//   }

//   groups.forEach(processGroup)

//   return result
// }
