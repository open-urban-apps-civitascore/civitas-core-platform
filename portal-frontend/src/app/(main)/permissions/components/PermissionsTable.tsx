import { CellContext, ColumnDef, createColumnHelper, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { RoleWithPermissions } from '@/types/permissions'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface PermissionsTableProps extends TableProps<RoleWithPermissions> {
  permissions: RoleWithPermissions[]
  shouldShowPermissionColumns?: boolean
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
    shouldShowPermissionColumns = false,
  } = props

  const t = useTranslations('permissions')

  const columnHelper = createColumnHelper<RoleWithPermissions>()

  const baseColumns = [
    columnHelper.accessor('name', {
      header: ({ column }) => {
        return <SortableTableHeader column={column} title={t('tableHeaders.name')} />
      },
      cell: info => info.getValue(),
      meta: {
        style: {
          width: shouldShowPermissionColumns ? '40%' : '100%',
        },
      },
    }),
  ]

  const permissionCellFunction = (info: CellContext<RoleWithPermissions, boolean>): string =>
    info.getValue() ? '✓' : ''
  const permissionColumns: ColumnDef<RoleWithPermissions, boolean>[] = [
    columnHelper.accessor('read', {
      header: t('tableHeaders.read'),
      cell: permissionCellFunction,
    }),
    columnHelper.accessor('create', {
      header: t('tableHeaders.create'),
      cell: permissionCellFunction,
    }),
    columnHelper.accessor('update', {
      header: t('tableHeaders.update'),
      cell: permissionCellFunction,
    }),
    columnHelper.accessor('delete', {
      header: t('tableHeaders.delete'),
      cell: permissionCellFunction,
    }),
    columnHelper.accessor('release', {
      header: t('tableHeaders.release'),
      cell: permissionCellFunction,
    }),
  ]

  const getColumns = () => {
    return shouldShowPermissionColumns ? [...baseColumns, ...permissionColumns] : baseColumns
  }

  const table = useReactTable({
    columns: getColumns(),
    data: permissions,
    getCoreRowModel: getCoreRowModel(),
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
      isPaginationHidden={true}
    />
  )
}
