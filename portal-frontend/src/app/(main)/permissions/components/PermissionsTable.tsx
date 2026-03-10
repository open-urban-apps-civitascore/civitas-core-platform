import { createColumnHelper, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Permission } from '@/types/permissions'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface PermissionsTableProps extends TableProps<Permission> {
  permissions: Permission[]
}

export const PermissionsTable = (props: PermissionsTableProps) => {
  const {
    permissions,
    isLoading,
    sorting,
    pageIndex,
    pageSize,
    totalPages,
    rowSelection,
    rowCount,
    onPaginationChange,
    onSortingChange,
  } = props

  const t = useTranslations('permissions')

  const columnHelper = createColumnHelper<Permission>()

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
      meta: {
        style: {
          width: '40%',
        },
      },
    }),
  ]

  const getColumns = () => {
    return columns
  }

  const table = useReactTable({
    columns: getColumns(),
    data: permissions,
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
    <DataTable table={table} pageIndex={pageIndex} pageSize={pageSize} totalPages={totalPages} isLoading={isLoading} />
  )
}
