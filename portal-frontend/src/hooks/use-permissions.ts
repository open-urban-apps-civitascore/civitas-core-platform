import { useCallback, useMemo } from 'react'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PermissionName, ScopedPermissionCheck } from '@/types/currentUser'

// NOTE: This hook provides two types of permission checks:
// 1. Flat checks (hasPermission, hasAnyPermission) that aggregate across all
//    assignments and discard scope information. Correct for tenant-level
//    permissions (users, groups, roles).
// 2. Scope-aware checks (hasScopedPermission) for resource-scoped permissions
//    (e.g. DATASET_CREATE on a specific dataset). Returns true if the user has
//    the permission via a TENANT assignment (global) OR via an assignment
//    matching the exact scopeType + scopeId.
export const usePermissions = () => {
  const { data: currentUser } = useGetCurrentUser()

  const permissions = useMemo(
    () => new Set<PermissionName>(currentUser?.assignments.flatMap(a => a.permissions) ?? []),
    [currentUser?.assignments],
  )

  const hasPermission = useCallback((permission: PermissionName) => permissions.has(permission), [permissions])

  const hasAnyPermission = useCallback(
    (...perms: PermissionName[]) => perms.some(p => permissions.has(p)),
    [permissions],
  )

  const assignments = currentUser?.assignments
  const hasScopedPermission = useCallback<ScopedPermissionCheck>(
    (permission, scopeType, scopeId) => {
      if (!permissions.has(permission)) return false
      return (
        assignments?.some(
          a =>
            a.permissions.includes(permission) &&
            (a.scopeType === 'TENANT' || (a.scopeType === scopeType && a.scopeId === scopeId)),
        ) ?? false
      )
    },
    [permissions, assignments],
  )

  return { hasPermission, hasAnyPermission, hasScopedPermission }
}
