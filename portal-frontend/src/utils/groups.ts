import { GroupBaseInfo, GroupData, GroupResponse, UpdateGroupData } from '@/types/groups'

export const mapFormGroupData = (groupResponse: GroupResponse | null): UpdateGroupData | null =>
  groupResponse
    ? {
        ...groupResponse,
        contact: groupResponse.contact || { id: '', displayName: '' },
        roles: groupResponse.roles || [],
        subgroups: groupResponse.subgroups.flatMap(subgroup => mapFormGroupData(subgroup) ?? []),
      }
    : null

export const mapApiGroupBaseInfoData = (formData: GroupBaseInfo) => {
  const groupBaseInfo = {
    ...formData,
    contact: formData.contact?.id ? formData.contact : null,
  }
  return groupBaseInfo
}
