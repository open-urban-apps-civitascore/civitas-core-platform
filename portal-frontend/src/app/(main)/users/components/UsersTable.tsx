import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { Check } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Badge } from '@/components/ui/badge'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

import { ListUser } from '../page'

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
    isLoading
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
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('authority', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('info.authority')} />,
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('department', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('info.department')} />,
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('role', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('info.role')} />,
      cell: info => <Badge variant="secondary">{info.getValue()}</Badge>,
    }),
    columnHelper.accessor('email', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('info.email')} />,
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('isactive', {
      header: t('info.active'),
      cell: info => (info.getValue() ? <Check /> : '-'),
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
