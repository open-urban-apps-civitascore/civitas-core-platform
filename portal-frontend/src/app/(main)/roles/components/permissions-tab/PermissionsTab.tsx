'use client'

import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { type JSX, useEffect, useMemo, useRef, useState } from 'react'

import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { useQueryParams } from '@/hooks/use-query-params'
import { Permission, PermissionItem } from '@/types/permissions'
import { ROLE_TYPES } from '@/types/roles'

import { CategoryList } from './CategoryList'
import { RoleTemplateSelect } from './RoleTemplateSelect'

interface PermissionsTabProps {
  onPermissionUpdate: (permissionIds: string[]) => void
  currentSelectedPermissionIds?: Permission['id'][]
  roleType: string
  hasPermissionsTabBeenSaved: boolean
  setHasPermissionsTabBeenSaved: (value: boolean) => void
  isDefaultRole: boolean
}

const mapPermissions = (permissionsInput: Permission[]): PermissionItem[] => {
  return permissionsInput.map(permission => ({
    name: permission.name,
    value: permission.id,
    category: { id: permission.category, title: permission.category },
  }))
}

export const PermissionsTab = (props: PermissionsTabProps): JSX.Element => {
  const {
    onPermissionUpdate,
    currentSelectedPermissionIds,
    roleType,
    hasPermissionsTabBeenSaved,
    setHasPermissionsTabBeenSaved,
    isDefaultRole,
  } = props
  const router = useRouter()
  const t = useTranslations('common')
  const tRoles = useTranslations('roles')
  const { getApiRequestParams, tabValue } = useQueryParams()
  const [roleTemplate, setRoleTemplate] = useState<string | null>(null)
  const [checkedPermissionItems, setCheckedPermissionItems] = useState<PermissionItem[]>([])
  const initializedForIds = useRef<string | null>(null)
  const [searchInput, setSearchInput] = useState<string>('')

  // Fetch all readonly (default) roles to use as permission templates.
  const rolesRequestParams = new URLSearchParams('readonly=true')
  const permissionsRequestParams = new URLSearchParams(
    // TODO - implement backend filtering by search term instead of fetching all permissions and filtering client-side
    getApiRequestParams({ pageIndex: 0, pageSize: 9999, search: searchInput }),
  )

  const { data: rolesData } = useGetRoles({ params: rolesRequestParams })

  const { data: permissionsData, isFetching: isFetchingPermissions } = useGetPermissions({
    params: permissionsRequestParams,
  })

  // Filter roles client-side by the current tab's roleType
  const allRoles = useMemo(
    () => (rolesData?.data || []).filter(role => role.roleType === (tabValue?.toUpperCase() ?? ROLE_TYPES.SYSTEM)),
    [rolesData?.data, tabValue],
  )
  const permissions = useMemo(() => mapPermissions(permissionsData?.data || []), [permissionsData?.data])

  const getUniqueCategories = (): string[] => {
    const seen = new Set<string>()
    permissions.forEach(permission => seen.add(permission.category.id))
    return Array.from(seen)
  }

  const categories = getUniqueCategories()

  const selectedPermissions = useMemo(() => {
    return permissions.filter(permission => currentSelectedPermissionIds?.includes(permission.value))
  }, [currentSelectedPermissionIds, permissions])

  useEffect(() => {
    if (permissions.length === 0) return
    const idsKey = (currentSelectedPermissionIds ?? []).slice().sort().join(',')
    if (initializedForIds.current === idsKey) return
    initializedForIds.current = idsKey
    setCheckedPermissionItems(selectedPermissions)
  }, [currentSelectedPermissionIds, selectedPermissions, permissions])

  useEffect(() => {
    if (roleTemplate) {
      const selectedRole = allRoles.find(role => role.id === roleTemplate)
      if (selectedRole) {
        const selectedRolePermissionIds = selectedRole.permissions.map(p => p.id)
        const selectedPermissions = permissions.filter(permission =>
          selectedRolePermissionIds.includes(permission.value),
        )
        setCheckedPermissionItems(selectedPermissions)
      }
    }
  }, [roleTemplate, allRoles, permissions])

  const arePermissionsTouched = useMemo(() => {
    if (!currentSelectedPermissionIds) return false

    const selectedIdsSet = new Set(currentSelectedPermissionIds)
    const checkedIdsSet = new Set(checkedPermissionItems.map(item => item.value))

    if (selectedIdsSet.size !== checkedIdsSet.size) return true

    return Array.from(selectedIdsSet).some(id => !checkedIdsSet.has(id))
  }, [checkedPermissionItems, currentSelectedPermissionIds])

  useEffect(() => {
    setHasPermissionsTabBeenSaved(false)
  }, [checkedPermissionItems, setHasPermissionsTabBeenSaved])

  if (isFetchingPermissions) {
    return <LoadingSpinner />
  }

  return (
    <div>
      <SearchHeader
        searchString={searchInput}
        onChangeSearchString={setSearchInput}
        customElement={
          isDefaultRole ? null : <RoleTemplateSelect allRoles={allRoles} setRoleTemplate={setRoleTemplate} />
        }
        placeholder={tRoles('permissionsTab.searchPermissions')}
      />

      {permissions.length === 0 ? (
        <div className="flex items-center justify-center text-sm mt-3.5">{t('noResults')}</div>
      ) : (
        <>
          {categories.map(category => (
            <div key={category}>
              <CategoryList
                permissionList={permissions.filter(permission => permission.category.id === category)}
                checkedItems={checkedPermissionItems}
                setCheckedItems={setCheckedPermissionItems}
                isDefaultRole={isDefaultRole}
              />
            </div>
          ))}

          {!isDefaultRole && (
            <ActionButtons
              confirmButtonType="button"
              onConfirmClick={() => onPermissionUpdate(checkedPermissionItems.map(item => item.value))}
              onCancelClick={() => router.push(`/roles?_tab=${roleType || ROLE_TYPES.SYSTEM}`)}
              isConfirmButtonDisabled={isFetchingPermissions || !arePermissionsTouched || hasPermissionsTabBeenSaved}
              isCancelButtonDisabled={isFetchingPermissions}
            />
          )}
        </>
      )}
    </div>
  )
}
