import { Group } from '@/types/groups'
import { BaseRole } from '@/types/roles'

export const mapRolesData = (roles: BaseRole[], groupData: Group[]) => {
  const roleMap = new Map(roles.map(role => [role.id, role]))

  const allRoles = groupData.flatMap(group => {
    if (!group.assignments) return []
    return group.assignments?.flatMap(assignment => {
      const currentRole = roleMap.get(assignment.role.id)
      if (!currentRole) return []
      return {
        id: `${group.id}-${currentRole.id}`,
        name: currentRole.name,
        inherited: false,
        group: group?.name || null,
        type: currentRole.roleType,
        roleId: currentRole.id,
      }
    })
  })
  return allRoles
}
