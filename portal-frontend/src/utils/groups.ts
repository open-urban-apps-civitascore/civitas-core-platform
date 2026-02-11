import { Group, GroupBaseFormData } from '@/types/groups'

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
  contactUser: groupData.contactUser?.id || '',
})

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
