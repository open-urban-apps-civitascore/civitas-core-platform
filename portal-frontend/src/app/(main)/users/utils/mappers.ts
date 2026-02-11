import { Group } from '@/types/groups'
import { BaseRole } from '@/types/roles'

export const mapRolesData = (roles: BaseRole[], groupData: Group[]) => {
  const roleMap = new Map(roles.map(role => [role.id, role]))

  const allRoles = groupData.flatMap(group =>
    group.roles.flatMap(groupRole => {
      const currentRole = roleMap.get(groupRole)
      if (!currentRole) return []
      return {
        id: `${group.id}-${currentRole.id}`,
        name: currentRole.name,
        inherited: false,
        group: group?.name || null,
        dataspace: group?.dataspace || null,
        type: currentRole.type,
        roleId: currentRole.id,
      }
    }),
  )
  return allRoles
}
