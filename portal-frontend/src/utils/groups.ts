import { Group, GroupBaseFormData } from '@/types/groups'

export const mapGroupDetailsData = (groupResponse: Group | null): Group | null =>
  groupResponse
    ? {
        ...groupResponse,
        contact: groupResponse.contact || { id: '', displayName: '' },
        roles: groupResponse.roles || [],
        subgroups: groupResponse.subgroups.flatMap(subgroup => mapGroupDetailsData(subgroup) ?? []),
      }
    : null

export const mapGroupToBaseFormData = (groupData: Group): GroupBaseFormData => ({
  id: groupData.id,
  title: groupData.title,
  description: groupData.description || '',
  contact: groupData.contact?.id || null,
})
