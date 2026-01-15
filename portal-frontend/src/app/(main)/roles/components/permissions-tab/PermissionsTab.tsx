'use client'

import { useQuery } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { type JSX, useEffect, useMemo, useState } from 'react'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Item } from '@/types/common'
import { Permission, PermissionItem } from '@/types/permissions'
import { ROLE_ORIGINS, ROLE_TYPES, RoleResponse } from '@/types/roles'

import { CategoryList } from './CategoryList'
import { RoleTemplateSelect } from './RoleTemplateSelect'

type PermissionsTabProps = {
  onPermissionUpdate: (permissionIds: string[]) => void
  currentSelectedPermissionIds?: Permission['id'][]
  roleType: string
  hasPermissionsTabBeenSaved: boolean
  setHasPermissionsTabBeenSaved: (value: boolean) => void
  isDefaultRole: boolean
}

const mapPermissions = (permissionsInput: Permission[]): PermissionItem[] => {
  return permissionsInput.map(permission => ({
    name: permission.title,
    value: permission.id,
    category: permission.category,
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
  const [searchInput, setSearchInput] = useState<string>('')

  const rolesRequestParams = new URLSearchParams(`type=${tabValue}&roleOrigin=${ROLE_ORIGINS.DEFAULT}`)
  const permissionsRequestParams = new URLSearchParams(
    `type=${tabValue}&${getApiRequestParams({ pageIndex: 0, pageSize: 9999, search: searchInput })}`,
  )

  const { data: rolesData, isFetching: isFetchingRoles } = useQuery({
    queryKey: ['roles', rolesRequestParams.toString()],
    queryFn: () =>
      apiRequest<RoleResponse[]>({
        endpoint: '/roles',
        method: 'GET',
        params: rolesRequestParams,
        errorMessage: 'An error occurred while fetching roles.',
      }),
    placeholderData: previousData => previousData,
  })

  const { data: permissionsData, isFetching: isFetchingPermissions } = useQuery({
    queryKey: ['permissions', permissionsRequestParams.toString()],
    queryFn: () =>
      apiRequest<Permission[]>({
        endpoint: '/permissions',
        method: 'GET',
        params: permissionsRequestParams,
        errorMessage: 'An error occurred while fetching permissions.',
      }),
    placeholderData: previousData => previousData,
  })

  const allRoles = useMemo(() => rolesData?.data || [], [rolesData?.data])
  const permissions = useMemo(() => mapPermissions(permissionsData?.data || []), [permissionsData?.data])

  const isLoading = isFetchingRoles || isFetchingPermissions

  const getUniqueCategories = (): Item[] => {
    const seenIds = new Set()
    const uniqueCategories: Item[] = []

    permissions.forEach(permission => {
      if (!seenIds.has(permission.category.id)) {
        seenIds.add(permission.category.id)
        uniqueCategories.push(permission.category)
      }
    })

    return uniqueCategories
  }

  const categories = getUniqueCategories()

  const selectedPermissions = useMemo(() => {
    return permissions.filter(permission => currentSelectedPermissionIds?.includes(permission.value))
  }, [currentSelectedPermissionIds, permissions])

  useEffect(() => {
    if (selectedPermissions.length > 0) {
      setCheckedPermissionItems(selectedPermissions)
    }
  }, [selectedPermissions])

  useEffect(() => {
    if (roleTemplate) {
      const selectedRole = allRoles.find(role => role.id === roleTemplate)
      if (selectedRole) {
        const selectedPermissions = permissions.filter(permission =>
          selectedRole?.permissions?.includes(permission.value),
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

    selectedIdsSet.forEach(id => {
      if (!checkedIdsSet.has(id)) return true
    })

    return false
  }, [checkedPermissionItems, currentSelectedPermissionIds])

  useEffect(() => {
    setHasPermissionsTabBeenSaved(false)
  }, [checkedPermissionItems, setHasPermissionsTabBeenSaved])

  if (isLoading) {
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
            <div key={category.id}>
              <CategoryList
                permissionList={permissions.filter(permission => permission.category.id === category.id)}
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
              isConfirmButtonDisabled={isLoading || !arePermissionsTouched || hasPermissionsTabBeenSaved}
              isCancelButtonDisabled={isLoading}
            />
          )}
        </>
      )}
    </div>
  )
}
