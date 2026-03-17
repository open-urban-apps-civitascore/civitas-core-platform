'use client'

import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { SearchHeader } from '@/components/search-area/SearchArea'
import { cn } from '@/lib/utils'
import { Permission } from '@/types/permissions'
import { ROLE_CATEGORIES } from '@/types/roles'

import { translatePermissionName } from '../utils/translatePermissionName'
import { PermissionsList } from './PermissionsList'

interface SystemPermissionsTabProps {
  permissions: Permission[]
  isLoading: boolean
}

const KNOWN_CATEGORIES = [ROLE_CATEGORIES.DATA.toString(), ROLE_CATEGORIES.TENANTADMINISTRATION.toString()] as const

export const SystemPermissionsTab = ({ permissions, isLoading }: SystemPermissionsTabProps) => {
  const t = useTranslations()

  const [search, setSearch] = useState('')
  const CATEGORY_OTHER = 'OTHER'

  const filteredPermissions = useMemo(() => {
    const translated = permissions.map(p => ({
      ...p,
      translatedName: translatePermissionName(p.name, t),
    }))
    return search ? translated.filter(p => p.translatedName.toLowerCase().includes(search.toLowerCase())) : translated
  }, [permissions, search, t])

  const groups = useMemo(
    () =>
      Object.groupBy(filteredPermissions, permission =>
        KNOWN_CATEGORIES.includes(permission.category) ? permission.category : CATEGORY_OTHER,
      ),
    [filteredPermissions],
  )

  const getCategoryLabel = (category: string) => {
    const translationKey = `permissions.systemPermissions.categories.${category}`
    return t.has(translationKey) ? t(translationKey) : category
  }

  return (
    <>
      <SearchHeader searchString={search} onChangeSearchString={setSearch} />

      {[...KNOWN_CATEGORIES, CATEGORY_OTHER].map((category, index) => {
        const groupedPermissions = groups[category] ?? []
        return (
          <div key={category} className={cn(index === 0 ? 'mt-0' : 'mt-4')}>
            <PermissionsList
              header={getCategoryLabel(category)}
              items={groupedPermissions.map(p => p.translatedName)}
              isLoading={isLoading}
            />
          </div>
        )
      })}
    </>
  )
}
