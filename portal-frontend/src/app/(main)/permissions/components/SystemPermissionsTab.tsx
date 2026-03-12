'use client'

import { useMemo } from 'react'

import { SearchHeader } from '@/components/search-area/SearchArea'
import { useQueryParams } from '@/hooks/use-query-params'
import { Permission } from '@/types/permissions'
import { ROLE_CATEGORIES } from '@/types/roles'

import { PermissionsList } from './PermissionsList'

interface SystemPermissionsTabProps {
  permissions: Permission[]
  isLoading: boolean
}

const KNOWN_CATEGORIES = [ROLE_CATEGORIES.DATA.toString(), ROLE_CATEGORIES.TENANTADMINISTRATION.toString()] as const

export const SystemPermissionsTab = ({ permissions, isLoading }: SystemPermissionsTabProps) => {
  const { setSearchParam, search } =
    useQueryParams()
  const CATEGORY_OTHER = 'OTHER'

  const groups = useMemo(
    () =>
      Object.groupBy(permissions, permission =>
        KNOWN_CATEGORIES.includes(permission.category) ? permission.category : CATEGORY_OTHER,
      ),
    [permissions],
  )

  return (
    <>
      <SearchHeader searchString={search} onChangeSearchString={setSearchParam} />

      {[...KNOWN_CATEGORIES, CATEGORY_OTHER].map(category => {
        const groupedPermissions = groups[category] ?? []
        return (
          <div key={category} className="mt-8">
            <PermissionsList header={category} items={groupedPermissions.map(p => p.name)} isLoading={isLoading} />
          </div>
        )
      })}
    </>
  )
}
