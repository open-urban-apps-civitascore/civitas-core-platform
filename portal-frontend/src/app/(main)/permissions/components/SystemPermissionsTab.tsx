'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { useState } from 'react'

import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { useQueryParams } from '@/hooks/use-query-params'
import { Permission } from '@/types/permissions'

import { PermissionsTable } from './PermissionsTable'

interface SystemPermissionsTabProps {
  permissions: Permission[]
  isLoading: boolean
  rowCount: number
}

export const SystemPermissionsTab = ({ permissions, isLoading, rowCount }: SystemPermissionsTabProps) => {
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const { setSortingParams, setPaginationParams, setSearchParam, pageIndex, pageSize, sorting, search, totalPages } =
    useQueryParams()

  return (
    <>
      <SearchHeader searchString={search} onChangeSearchString={setSearchParam} />
      <TableContainer>
        <PermissionsTable
          permissions={permissions}
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
