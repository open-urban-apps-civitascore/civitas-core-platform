import { createColumnHelper, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'

import { RoleResponse } from '../../../../../types/roles'
import { TableProps } from '../../../../../types/table'

interface RolesTableProps extends TableProps<RoleResponse> {
  roles: RoleResponse[]
}

export const RolesTable = (props: RolesTableProps) => {
  const {
    roles,
    isLoading,
    sorting,
    pageIndex,
    pageSize,
    totalPages,
    rowSelection,
    rowCount,
    onRowClick,
    onPaginationChange,
    onSortingChange,
  } = props

  const t = useTranslations('roles')

  const locale = useLocale()

  const columnHelper = createColumnHelper<RoleResponse>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'ID',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => {
        return <SortableTableHeader column={column} title={t('tableHeaders.name')} />
      },
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('description', {
      header: () => t('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '35%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('createdAt', {
      header: () => t('tableHeaders.createdAt'),
      cell: info => formatDate(info.getValue(), locale),
    }),
    columnHelper.accessor('user', {
      header: () => t('tableHeaders.user'),
      cell: info => info.getValue()?.length,
    }),
    columnHelper.accessor('permissions', {
      header: () => t('tableHeaders.permissions'),
      cell: info => info.getValue()?.length,
    }),
  ]

  const table = useReactTable({
    columns: columns,
    data: roles,
    getCoreRowModel: getCoreRowModel(),
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    rowCount,
    manualPagination: true,
    manualSorting: true,
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    onPaginationChange: updater => {
      onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  return (
    <DataTable
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      isLoading={isLoading}
      onRowClick={onRowClick}
    />
  )
}
