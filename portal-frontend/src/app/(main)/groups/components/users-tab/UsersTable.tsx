import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { StatusLabel } from '@/components/status-label/StatusLabel'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { TableProps } from '@/types/table'
import { GroupListUser } from '@/types/users'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'

interface UsersTableProps extends TableProps<GroupListUser> {
  users: GroupListUser[]
}

export const UsersTable = (props: UsersTableProps) => {
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
  const locale = useLocale()
  const t = useTranslations('groups')
  const tUsers = useTranslations('users')
  const columnHelper = createColumnHelper<GroupListUser>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('fullName', {
      header: ({ column }) => <SortableTableHeader column={column} title={tUsers('info.displayName')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('authority', {
      header: ({ column }) => <SortableTableHeader column={column} title={tUsers('info.authority')} />,
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('department', {
      header: ({ column }) => <SortableTableHeader column={column} title={tUsers('info.department')} />,
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('email', {
      header: ({ column }) => <SortableTableHeader column={column} title={tUsers('info.email')} />,
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('assignedAt', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('users.assignedAt')} />,
      cell: info => formatDate(info.getValue(), locale),
    }),
    columnHelper.accessor('isActive', {
      header: tUsers('info.status.active'),
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

  if (isLoading) {
    return <LoadingSpinner className="h-full" />
  }

  return (
    <DataTable
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      onRowClick={onRowClick}
    />
  )
}
