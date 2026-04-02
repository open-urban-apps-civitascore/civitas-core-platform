import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { Assignment, ASSIGNMENT_SCOPE_TYPES, AssignmentScopedInput } from '@/types/assignments'

export const mapAssignmentApiResponseToTable = (assignments: Assignment[]): GroupRoleAssignmentTable[] => {
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
      groupMap.set(groupId, {
        groupId: groupId,
        groupName: assignment.group.name,
        groupDescription: assignment.group.description,
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

export const mapGroupRoleAssignmentsToApiPayload = (
  groupRoleAssignments: GroupRoleAssignmentTable[],
): AssignmentScopedInput[] => {
  const assignments: AssignmentScopedInput[] = []

  groupRoleAssignments?.forEach(groupRoleAssignment => {
    groupRoleAssignment.assignedRoles.forEach(assignedRole => {
      assignments.push({
        groupId: groupRoleAssignment.groupId,
        roleId: assignedRole.roleId,
      })
    })
  })

  return assignments
}

export const toAssignmentSet = (groups: GroupRoleAssignmentTable[]): Set<string> =>
  new Set(groups?.flatMap(g => g.assignedRoles.map(r => `${g.groupId}::${r.roleId}`)))

export const hasAssignmentChanges = (
  current: GroupRoleAssignmentTable[],
  initial: GroupRoleAssignmentTable[],
): boolean => {
  const currentSet = toAssignmentSet(current)
  const initialSet = toAssignmentSet(initial)
  return currentSet.size !== initialSet.size || [...currentSet].some(item => !initialSet.has(item))
}

export const isPlatformwideAssignment = (assignment: Assignment) =>
  assignment.scopeType === ASSIGNMENT_SCOPE_TYPES.TENANT || assignment.scopeType === null
