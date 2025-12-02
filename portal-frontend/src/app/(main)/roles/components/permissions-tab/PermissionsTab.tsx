'use client'

import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { type JSX, useCallback, useEffect, useMemo, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Item } from '@/types/common'
import { Permission, PermissionItem } from '@/types/permissions'
import { ROLE_ORIGINS, ROLE_TYPES, RoleResponse } from '@/types/roles'

import { CategoryList } from './CategoryList'
import { RoleTemplateSelect } from './RoleTemplateSelect'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

type PermissionsTabProps = {
  updatePermissions: (permissionIds: string[]) => void
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
    updatePermissions,
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
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [allPermissionsResponse, setAllPermissionsResponse] = useState<Permission[]>([])
  const [allRoles, setAllRoles] = useState<RoleResponse[]>([])
  const [roleTemplate, setRoleTemplate] = useState<string | null>(null)
  const [checkedPermissionItems, setCheckedPermissionItems] = useState<PermissionItem[]>([])
  const [searchInput, setSearchInput] = useState<string>('')

  const getRoles = useCallback(async () => {
    try {
      const rolesResponse = await fetch(`${URL}/roles?type=${tabValue}&roleOrigin=${ROLE_ORIGINS.DEFAULT}`, {
        cache: 'no-store',
      })

      if (!rolesResponse.ok) {
        throw new Error('An error occurred while loading roles data')
      }

      const rolesData: RoleResponse[] = await rolesResponse.json()
      setAllRoles(rolesData)
    } catch (error) {
      console.error(error)
    }
  }, [tabValue])

  useEffect(() => {
    getRoles()
  }, [getRoles])

  const getPermissions = useCallback(async () => {
    const requestParams = getApiRequestParams({ pageIndex: 0, pageSize: 9999, search: searchInput })

    try {
      setIsLoading(true)
      const permissionResponse = await fetch(`${URL}/permissions?type=${tabValue}&${requestParams.toString()}`, {
        cache: 'no-store',
      })
      if (!permissionResponse.ok) {
        throw new Error('An error occurred while loading permissions data')
      }

      const permissionsData: Permission[] = await permissionResponse.json()

      setAllPermissionsResponse(permissionsData)
      setIsLoading(false)
    } catch (error) {
      console.error(error)
      setIsLoading(false)
      throw new Error('An error occurred while loading permissions data')
    }
  }, [searchInput, getApiRequestParams, tabValue])

  useEffect(() => {
    getPermissions()
  }, [getPermissions])

  const permissions = useMemo(() => mapPermissions(allPermissionsResponse), [allPermissionsResponse])

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
              onConfirmClick={() => updatePermissions(checkedPermissionItems.map(item => item.value))}
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
