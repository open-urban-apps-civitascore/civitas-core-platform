'use client'

import { useTranslations } from 'next-intl'
import { type JSX, useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { useQueryParams } from '@/hooks/use-query-params'
import { Permission, PermissionItem } from '@/types/permissions'
import { ROLE_TYPES, RoleType } from '@/types/roles'

import { CategoryList } from './CategoryList'
import { DataPermissionsGrid } from './DataPermissionsGrid'
import { RoleTemplateSelect } from './RoleTemplateSelect'

interface PermissionsTabProps {
  pendingPermissionIds: string[]
  onPendingPermissionIdsChange: (ids: string[]) => void
  isReadOnly: boolean
  currentRoleId?: string
  roleType: RoleType
}

const formatPermissionName = (name: string): string =>
  name
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ')

const mapPermissions = (permissionsInput: Permission[]): PermissionItem[] => {
  return permissionsInput.map(permission => ({
    name: formatPermissionName(permission.name),
    value: permission.id,
    category: { id: permission.category, title: permission.category },
  }))
}

export const PermissionsTab = (props: PermissionsTabProps): JSX.Element => {
  const { pendingPermissionIds, onPendingPermissionIdsChange, isReadOnly, currentRoleId, roleType } = props
  const t = useTranslations('common')
  const tRoles = useTranslations('roles')
  const { getApiRequestParams } = useQueryParams()
  const [roleTemplate, setRoleTemplate] = useState<string | null>(null)
  const [checkedPermissionItems, setCheckedPermissionItems] = useState<PermissionItem[]>([])
  const initializedForIds = useRef<string | null>(null)
  const [searchInput, setSearchInput] = useState<string>('')

  const rolesRequestParams = new URLSearchParams('readonly=true')
  const permissionsRequestParams = new URLSearchParams(
    getApiRequestParams({ pageIndex: 0, pageSize: 9999, search: searchInput }),
  )

  const { data: rolesData } = useGetRoles({ params: rolesRequestParams })

  const { data: permissionsData, isFetching: isFetchingPermissions } = useGetPermissions({
    params: permissionsRequestParams,
  })

  const allRoles = useMemo(
    () => (rolesData?.data || []).filter(role => role.roleType === roleType),
    [rolesData?.data, roleType],
  )
  const permissions = useMemo(() => mapPermissions(permissionsData?.data || []), [permissionsData?.data])

  const getUniqueCategories = (): string[] => {
    const seen = new Set<string>()
    permissions.forEach(permission => seen.add(permission.category.id))
    return Array.from(seen)
  }

  const categories = getUniqueCategories()

  // Initialize checked items from pending permission IDs
  const selectedPermissions = useMemo(() => {
    return permissions.filter(permission => pendingPermissionIds.includes(permission.value))
  }, [pendingPermissionIds, permissions])

  useEffect(() => {
    if (permissions.length === 0) return
    const idsKey = pendingPermissionIds.slice().sort().join(',')
    if (initializedForIds.current === idsKey) return
    initializedForIds.current = idsKey
    setCheckedPermissionItems(selectedPermissions)
  }, [pendingPermissionIds, selectedPermissions, permissions])

  // Sync checked items to parent
  const handleCheckedItemsChange = useCallback(
    (items: PermissionItem[]) => {
      setCheckedPermissionItems(items)
      onPendingPermissionIdsChange(items.map(item => item.value))
    },
    [onPendingPermissionIdsChange],
  )

  // When template is selected
  useEffect(() => {
    if (roleTemplate) {
      const selectedRole = allRoles.find(role => role.id === roleTemplate)
      if (selectedRole) {
        const selectedRolePermissionIds = (selectedRole.permissions ?? []).map(p => p.id)
        const selected = permissions.filter(permission => selectedRolePermissionIds.includes(permission.value))
        handleCheckedItemsChange(selected)
      }
    }
  }, [roleTemplate, allRoles, permissions, handleCheckedItemsChange])

  if (isFetchingPermissions) {
    return <LoadingSpinner />
  }

  return (
    <div>
      <SearchHeader
        searchString={searchInput}
        onChangeSearchString={setSearchInput}
        customElement={
          isReadOnly ? null : (
            <RoleTemplateSelect allRoles={allRoles} setRoleTemplate={setRoleTemplate} currentRoleId={currentRoleId} />
          )
        }
        placeholder={tRoles('permissionsTab.searchPermissions')}
      />

      {permissions.length === 0 ? (
        <div className="flex items-center justify-center text-sm mt-3.5">{t('noResults')}</div>
      ) : roleType === ROLE_TYPES.DATA ? (
        <DataPermissionsGrid
          permissions={permissions}
          checkedItems={checkedPermissionItems}
          setCheckedItems={handleCheckedItemsChange}
          isReadOnly={isReadOnly}
        />
      ) : (
        categories.map(category => (
          <div key={category}>
            <CategoryList
              permissionList={permissions.filter(permission => permission.category.id === category)}
              checkedItems={checkedPermissionItems}
              setCheckedItems={handleCheckedItemsChange}
              isReadOnly={isReadOnly}
            />
          </div>
        ))
      )}
    </div>
  )
}
