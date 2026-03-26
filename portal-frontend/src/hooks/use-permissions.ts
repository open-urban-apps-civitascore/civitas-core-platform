import { useCallback, useMemo } from 'react'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
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

  return { hasPermission, hasAnyPermission, hasScopedPermission }
}
