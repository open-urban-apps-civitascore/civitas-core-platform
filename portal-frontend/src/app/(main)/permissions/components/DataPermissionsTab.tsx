'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { useState } from 'react'

import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { useQueryParams } from '@/hooks/use-query-params'
import { Permission, RoleWithPermissions } from '@/types/permissions'

import { PermissionsTable } from './PermissionsTable'

interface DataPermissionsTabProps {
  permissions: Permission[]
  isLoading: boolean
  rowCount: number
}

export const DataPermissionsTab = ({ permissions, isLoading, rowCount }: DataPermissionsTabProps) => {
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const { setSortingParams, setPaginationParams, setSearchParam, pageIndex, pageSize, sorting, search, totalPages } =
    useQueryParams()

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
      name,
      read: actions.has('read'),
      create: actions.has('create'),
      update: actions.has('update'),
      delete: actions.has('delete'),
      release: actions.has('release'),
    }))
  }

  return (
    <>
      <SearchHeader searchString={search} onChangeSearchString={setSearchParam} />
      <TableContainer>
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
      </TableContainer>
    </>
  )
}
