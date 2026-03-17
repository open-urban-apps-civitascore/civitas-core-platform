'use client'

import { useTranslations } from 'next-intl'
import { useMemo } from 'react'

import { SearchHeader } from '@/components/search-area/SearchArea'
import { useQueryParams } from '@/hooks/use-query-params'
import { cn } from '@/lib/utils'
import { Permission } from '@/types/permissions'
import { ROLE_CATEGORIES } from '@/types/roles'

import { PermissionsList } from './PermissionsList'

interface SystemPermissionsTabProps {
  permissions: Permission[]
  isLoading: boolean
}

const KNOWN_CATEGORIES = [ROLE_CATEGORIES.DATA.toString(), ROLE_CATEGORIES.TENANTADMINISTRATION.toString()] as const

export const SystemPermissionsTab = ({ permissions, isLoading }: SystemPermissionsTabProps) => {
  const t = useTranslations()

  const { setSearchParam, search } = useQueryParams()
  const CATEGORY_OTHER = 'OTHER'

  const groups = useMemo(
    () =>
      Object.groupBy(permissions, permission =>
        KNOWN_CATEGORIES.includes(permission.category) ? permission.category : CATEGORY_OTHER,
      ),
    [permissions],
  )

  const getCategoryLabel = (category: string) => {
    const translationKey = `permissions.systemPermissions.categories.${category}`
    return t.has(translationKey) ? t(translationKey) : category
  }

  return (
    <>
      <SearchHeader searchString={search} onChangeSearchString={setSearchParam} />

      {[...KNOWN_CATEGORIES, CATEGORY_OTHER].map((category, index) => {
        const groupedPermissions = groups[category] ?? []
        return (
          <div key={category} className={cn(index === 0 ? 'mt-0' : 'mt-4')}>
            <PermissionsList
              header={getCategoryLabel(category)}
              items={groupedPermissions.map(p => p.name)}
              isLoading={isLoading}
            />
          </div>
        )
      })}
    </>
  )
}
