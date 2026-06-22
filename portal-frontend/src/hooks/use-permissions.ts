import { useCallback, useMemo } from 'react'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { ASSIGNMENT_SCOPE_TYPES, AssignmentScope } from '@/types/assignments'
import { MeAssignment, PermissionName, ScopedPermissionCheck } from '@/types/currentUser'

// Scope types whose permissions are valid on a DATAPOOL-scoped assignment.
// A DATAPOOL assignment may carry DATASET_* permissions to express that those
// permissions apply to all datasets inside the pool.
const DATAPOOL_ALLOWED_PREFIXES = ['DATAPOOL', 'DATASET']

// Filters permissions per assignment so that non-TENANT scoped assignments
// only retain permissions matching their scope type (e.g. a DATASOURCE-scoped
// assignment only keeps DATASOURCE_* permissions). DATAPOOL-scoped assignments
// additionally allow DATASET_* permissions (pool → dataset cascade).
const filterAssignmentPermissions = (assignment: MeAssignment): MeAssignment => {
  if (!assignment.scopeType || assignment.scopeType === ASSIGNMENT_SCOPE_TYPES.TENANT) return assignment
  if (assignment.scopeType === ASSIGNMENT_SCOPE_TYPES.DATAPOOL) {
    return {
      ...assignment,
      permissions: assignment.permissions.filter(p => DATAPOOL_ALLOWED_PREFIXES.some(prefix => p.startsWith(prefix))),
    }
  }
  const prefix = assignment.scopeType
  return {
    ...assignment,
    permissions: assignment.permissions.filter(p => p.startsWith(prefix)),
  }
}

export const usePermissions = () => {
  const { data: currentUser } = useGetCurrentUser()

  const filteredAssignments = useMemo(
    () => currentUser?.assignments.map(filterAssignmentPermissions) ?? [],
    [currentUser?.assignments],
  )

  const permissions = useMemo(
    () => new Set<PermissionName>(filteredAssignments.flatMap(a => a.permissions)),
    [filteredAssignments],
  )

  const hasPermission = useCallback((permission: PermissionName) => permissions.has(permission), [permissions])

  // Use this when a permission is only meaningful within a specific scope type (e.g. CREATE
  // actions require TENANT scope). Unlike hasScopedPermission, this checks scope type only
  // — not a specific resource ID.
  const hasPermissionInScope = useCallback(
    (permission: PermissionName, scopeType: AssignmentScope) =>
      filteredAssignments.some(a => a.scopeType === scopeType && a.permissions.includes(permission)),
    [filteredAssignments],
  )

  const hasAnyPermission = useCallback(
    (...perms: PermissionName[]) => perms.some(p => permissions.has(p)),
    [permissions],
  )

  const hasScopedPermission = useCallback<ScopedPermissionCheck>(
    (permission, scopeType, scopeId, datapoolId?) => {
      if (!permissions.has(permission)) return false
      return filteredAssignments.some(
        a =>
          a.permissions.includes(permission) &&
          (a.scopeType === ASSIGNMENT_SCOPE_TYPES.TENANT ||
            (a.scopeType === scopeType && a.scopeId === scopeId) ||
            (scopeType === ASSIGNMENT_SCOPE_TYPES.DATASET &&
              datapoolId != null &&
              a.scopeType === ASSIGNMENT_SCOPE_TYPES.DATAPOOL &&
              a.scopeId === datapoolId)),
      )
    },
    [permissions, filteredAssignments],
  )

  return { hasPermission, hasPermissionInScope, hasAnyPermission, hasScopedPermission }
}
