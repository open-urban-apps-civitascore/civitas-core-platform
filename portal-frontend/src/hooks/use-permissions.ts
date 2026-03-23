import { useCallback, useMemo } from 'react'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PermissionName } from '@/types/currentUser'

// NOTE: This hook flattens all assignments into a single permission set,
// discarding scopeType/scopeId. This is correct for tenant-level permissions
// (users, groups, roles) but will need scope-aware overloads for resource-scoped
// permissions (e.g. DATASET_CREATE on a specific dataset).
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

  return { hasPermission, hasAnyPermission }
}
