'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { useQueryParams } from '@/hooks/use-query-params'
import { Permission, RoleWithPermissions } from '@/types/permissions'

import { PermissionsTable } from './PermissionsTable'

interface DataPermissionsTabProps {
  permissions: Permission[]
  isLoading: boolean
  rowCount: number
}

export const DataPermissionsTab = ({ permissions, isLoading, rowCount }: DataPermissionsTabProps) => {
  const t = useTranslations('permissions')

  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const { setSortingParams, setPaginationParams, pageIndex, pageSize, sorting, totalPages } = useQueryParams()

  const getRolesWithPermissions = (): RoleWithPermissions[] => {
    const groupedByEntity = new Map<string, Set<string>>()

    for (const permission of permissions) {
      const lastUnderscoreIndex = permission.name.lastIndexOf('_')
      const entityName = permission.name.substring(0, lastUnderscoreIndex)
      const action = permission.name.substring(lastUnderscoreIndex + 1).toLowerCase()

      if (!groupedByEntity.has(entityName)) {
        groupedByEntity.set(entityName, new Set())
      }
      groupedByEntity.get(entityName)!.add(action)
    }

    return Array.from(groupedByEntity.entries()).map(([name, actions]) => ({
      name: t(`values.${name}`, { defaultValue: name }),
      read: actions.has('read'),
      create: actions.has('create'),
      update: actions.has('update'),
      delete: actions.has('delete'),
      release: actions.has('release'),
    }))
  }

  return (
    <PermissionsTable
      permissions={getRolesWithPermissions()}
      shouldShowPermissionColumns
      isLoading={isLoading}
      rowCount={rowCount}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      sorting={sorting}
      rowSelection={rowSelection}
      setRowSelection={setRowSelection}
      onSortingChange={setSortingParams}
      onPaginationChange={setPaginationParams}
    />
  )
}
