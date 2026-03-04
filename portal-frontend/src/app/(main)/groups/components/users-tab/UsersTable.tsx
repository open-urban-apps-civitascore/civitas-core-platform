import { createColumnHelper, getCoreRowModel, getSortedRowModel, Row, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { TableProps } from '@/types/table'
import { ListUser } from '@/types/users'
import { resolveUpdater } from '@/utils/table'

interface UsersTableProps extends TableProps<ListUser> {
  users: ListUser[]
  onRemoveUserClick: (userId: string) => void
  isReadOnly: boolean
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
    onRemoveUserClick,
    isLoading,
    isReadOnly,
  } = props
  const tUsers = useTranslations('users')
  const tCommon = useTranslations('common')

  const columnHelper = createColumnHelper<ListUser>()

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
    columnHelper.accessor('email', {
      header: ({ column }) => <SortableTableHeader column={column} title={tUsers('info.email')} />,
      cell: info => info.getValue(),
    }),
    ...(!isReadOnly
      ? [
          {
            id: 'actions',
            cell: ({ row }: { row: Row<ListUser> }) => (
              <TableDropdownMenu
                menuItems={[
                  {
                    label: tCommon('actions.removeItem', { item: tCommon('items.user') }),
                    onClick: () => onRemoveUserClick(row.id),
                  },
                ]}
              />
            ),
            meta: {
              style: {
                width: '50px',
              },
            },
          },
        ]
      : []),
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
