import { useCallback, useMemo } from 'react'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { AssignmentScope } from '@/types/assignments'
import { MeAssignment, PermissionName, ScopedPermissionCheck } from '@/types/currentUser'

// Filters permissions per assignment so that non-TENANT scoped assignments
// only retain permissions matching their scope type (e.g. a DATASOURCE-scoped
// assignment only keeps DATASOURCE_* permissions).
const filterAssignmentPermissions = (assignment: MeAssignment): MeAssignment => {
  if (!assignment.scopeType || assignment.scopeType === 'TENANT') return assignment
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
    (permission, scopeType, scopeId) => {
      if (!permissions.has(permission)) return false
      return filteredAssignments.some(
        a =>
          a.permissions.includes(permission) &&
          (a.scopeType === 'TENANT' || (a.scopeType === scopeType && a.scopeId === scopeId)),
      )
    },
    [permissions, filteredAssignments],
  )

  return { hasPermission, hasPermissionInScope, hasAnyPermission, hasScopedPermission }
}
