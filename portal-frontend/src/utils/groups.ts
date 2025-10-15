import { GroupData, GroupResponse, UpdateGroupData } from '@/types/groups'

export const mapFormGroupData = (groupResponse: GroupResponse | null): UpdateGroupData | null =>
  groupResponse
    ? {
        ...groupResponse,
        contact: groupResponse.contact || { id: '', displayName: '' },
        roles: groupResponse.roles || [],
        subgroups: groupResponse.subgroups.flatMap(subgroup => mapFormGroupData(subgroup) ?? []),
      }
    : null

export const mapApiGroupData = (formData: GroupData) => {
  const groupData = {
    ...formData,
    contact: formData.contact?.id ? formData.contact : null,
  }
  return groupData
}
