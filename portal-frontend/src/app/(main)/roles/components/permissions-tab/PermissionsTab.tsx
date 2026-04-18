'use client'

import { useTranslations } from 'next-intl'
import { type JSX, useCallback, useEffect, useMemo, useState } from 'react'

import { translatePermissionName } from '@/app/(main)/permissions/utils/translatePermissionName'
import { useGetPermissions } from '@/app/services/api/permissions/clientRequests'
import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
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

type TranslationFn = {
  (key: string, params?: Record<string, string>): string
  has: (key: string) => boolean
}

const mapPermissions = (permissionsInput: Permission[], t: TranslationFn): PermissionItem[] => {
  return permissionsInput.map(permission => {
    const categoryKey = `permissions.systemPermissions.categories.${permission.category}`
    const categoryTitle = t.has(categoryKey) ? t(categoryKey) : permission.category
    return {
      name: translatePermissionName(permission.name, t),
      value: permission.id,
      category: { id: permission.category, title: categoryTitle },
    }
  })
}

export const PermissionsTab = (props: PermissionsTabProps): JSX.Element => {
  const { pendingPermissionIds, onPendingPermissionIdsChange, isReadOnly, currentRoleId, roleType } = props
  const t = useTranslations()
  const tRoles = useTranslations('roles')
  const tCommon = useTranslations('common')
  const [roleTemplate, setRoleTemplate] = useState<string | null>(null)
  const [checkedPermissionItems, setCheckedPermissionItems] = useState<PermissionItem[]>([])
  const [searchInput, setSearchInput] = useState<string>('')

  const rolesRequestParams = useMemo(() => {
    const params = new URLSearchParams()
    params.set('readonly', 'true')
    params.set('roleType', roleType)
    return params
  }, [roleType])
  const permissionsRequestParams = new URLSearchParams()
  if (searchInput.trim()) {
    permissionsRequestParams.set('q', searchInput.trim())
  }
  permissionsRequestParams.set('permissionType', roleType === ROLE_TYPES.DATA ? 'DATA' : 'SYSTEM')

  const { data: templateRolesResponse, error: getRolesError } = useGetRoles({ params: rolesRequestParams })
  const {
    data: permissionsData,
    isFetching: isFetchingPermissions,
    error: getPermissionsError,
  } = useGetPermissions({
    params: permissionsRequestParams,
  })

  const templateRoles = templateRolesResponse?.data
  const permissions = useMemo(() => mapPermissions(permissionsData?.data || [], t), [permissionsData?.data, t])

  const getUniqueCategories = (): string[] => {
    const seen = new Set<string>()
    permissions.forEach(permission => seen.add(permission.category.id))
    return Array.from(seen)
  }

  const categories = getUniqueCategories()

  // Sync checked items to reflect the visible permissions that are selected
  const visibleSelectedPermissions = useMemo(() => {
    return permissions.filter(permission => pendingPermissionIds.includes(permission.value))
  }, [pendingPermissionIds, permissions])

  useEffect(() => {
    setCheckedPermissionItems(visibleSelectedPermissions)
  }, [visibleSelectedPermissions])

  // Sync checked items to parent, preserving selections hidden by search filter
  const handleCheckedItemsChange = useCallback(
    (items: PermissionItem[]) => {
      const visiblePermissionIds = new Set(permissions.map(p => p.value))
      // Keep previously selected IDs that are not in the current visible (filtered) permissions list
      const hiddenSelectedIds = pendingPermissionIds.filter(id => !visiblePermissionIds.has(id))
      const newIds = [...items.map(item => item.value), ...hiddenSelectedIds]
      setCheckedPermissionItems(items)
      onPendingPermissionIdsChange(newIds)
    },
    [onPendingPermissionIdsChange, permissions, pendingPermissionIds],
  )

  // When template is selected
  useEffect(() => {
    if (roleTemplate) {
      const selectedRole = templateRoles?.find(role => role.id === roleTemplate)
      if (selectedRole) {
        const selectedRolePermissionIds = (selectedRole.permissions ?? []).map(p => p.id)
        const selected = permissions.filter(permission => selectedRolePermissionIds.includes(permission.value))
        handleCheckedItemsChange(selected)
      }
      setRoleTemplate(null)
    }
  }, [roleTemplate, templateRoles, permissions, handleCheckedItemsChange])

  if (isFetchingPermissions) {
    return <LoadingSpinner />
  }

  if (getRolesError || getPermissionsError) {
    return <NoDataPage className="h-full" title={tCommon('errors.loadingError')} />
  }

  return (
    <div>
      {roleType === ROLE_TYPES.DATA ? (
        !isReadOnly && (
          <div className="mb-4 flex justify-end">
            <RoleTemplateSelect
              templateRoles={templateRoles}
              setRoleTemplate={setRoleTemplate}
              currentRoleId={currentRoleId}
            />
          </div>
        )
      ) : (
        <SearchHeader
          searchString={searchInput}
          onChangeSearchString={setSearchInput}
          customElement={
            isReadOnly ? null : (
              <RoleTemplateSelect
                templateRoles={templateRoles}
                setRoleTemplate={setRoleTemplate}
                currentRoleId={currentRoleId}
              />
            )
          }
          placeholder={tRoles('permissionsTab.searchPermissions')}
        />
      )}

      {permissions.length === 0 ? (
        <div className="flex items-center justify-center text-sm mt-3.5">{tCommon('noResults')}</div>
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
