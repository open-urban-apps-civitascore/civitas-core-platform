import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { Assignment, CreateAssignmentData } from '@/types/assignments'
import { Group } from '@/types/groups'

export const mapAssignmentApiResponseToTable = (
  assignments: Assignment[],
  groups: Group[],
): GroupRoleAssignmentTable[] => {
  const groupMap = new Map<
    string,
    {
      groupId: string
      groupName: string
      groupDescription?: string
      assignedRoles: { roleId: string; roleName: string }[]
    }
  >()

  assignments.forEach(assignment => {
    const groupId = assignment.group.id

    if (groupMap.has(groupId)) {
      // Group already exists, add role
      const existing = groupMap.get(groupId)!
      existing.assignedRoles.push({
        roleId: assignment.role.id,
        roleName: assignment.role.name,
      })
    } else {
      // New group, create entry
      const groupDetails = groups.find(g => g.id === groupId)
      groupMap.set(groupId, {
        groupId: groupId, // Use group ID as unique key
        groupName: assignment.group.name,
        groupDescription: groupDetails?.description,
        assignedRoles: [
          {
            roleId: assignment.role.id,
            roleName: assignment.role.name,
          },
        ],
      })
    }
  })

  return Array.from(groupMap.values())
}

export const mapGroupRoleAssignmentsToCreateData = (
  groupRoleAssignments: GroupRoleAssignmentTable[],
): CreateAssignmentData[] => {
  const assignments: CreateAssignmentData[] = []

  groupRoleAssignments.forEach(groupRoleAssignment => {
    groupRoleAssignment.assignedRoles.forEach(assignedRole => {
      assignments.push({
        groupId: groupRoleAssignment.groupId,
        roleId: assignedRole.roleId,
      })
    })
  })

  return assignments
}
