import { Group } from '@/types/groups'

export const mapGroupDetailsData = (groupResponse: Group | null): Group | null =>
  groupResponse
    ? {
        ...groupResponse,
        contact: groupResponse.contact || { id: '', displayName: '' },
        roles: groupResponse.roles || [],
        subgroups: groupResponse.subgroups.flatMap(subgroup => mapGroupDetailsData(subgroup) ?? []),
      }
    : null
