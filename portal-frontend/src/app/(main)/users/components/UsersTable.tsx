import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { StatusLabel } from '@/components/status-label/StatusLabel'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { TableProps } from '@/types/table'
import { ListUser } from '@/types/users'
import { resolveUpdater } from '@/utils/table'

interface UsersTableProps extends TableProps<ListUser> {
  users: ListUser[]
}

const UsersTable = (props: UsersTableProps) => {
  const {
    users,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onRowClick,
    onPaginationChange,
    onSortingChange,
    isLoading,
  } = props
  const t = useTranslations('users')
  const columnHelper = createColumnHelper<ListUser>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('displayName', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('info.displayName')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '27%',
          minWidth: '200px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('authority', {
      header: t('info.authority'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '18%',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('department', {
      header: t('info.department'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('email', {
      header: t('info.email'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('isActive', {
      header: t('info.active'),
      cell: info => <StatusLabel isChecked={info.getValue()} />,
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: users,
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
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
      onRowClick={onRowClick}
      isLoading={isLoading}
    />
  )
}

export default UsersTable
