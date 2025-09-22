import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'

import { TableProps } from '../../../../../types/table'
import { User } from '../page'

interface UsersTableProps extends TableProps {
  users: User[]
}

const UsersTable = (props: UsersTableProps) => {
  const { users, rowCount, pageIndex, totalPages, setPageIndex, pageSize, setPageSize, sorting, setSorting } = props
  const t = useTranslations('users')
  const columnHelper = createColumnHelper<User>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('displayName', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.displayName')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('firstName', {
      header: t('tableHeaders.firstName'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('lastName', {
      header: t('tableHeaders.lastName'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('email', {
      header: t('tableHeaders.email'),
      cell: info => info.getValue(),
    }),
  ]

  const table = useReactTable({
    columns: columns,
    data: users,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    manualPagination: true,
    manualSorting: true,
    state: { pagination: { pageIndex, pageSize }, sorting },
    onPaginationChange: updater => {
      const newPagination = typeof updater === 'function' ? updater({ pageIndex, pageSize }) : updater
      setPageIndex(newPagination.pageIndex)
      setPageSize(newPagination.pageSize)
    },
    onSortingChange: setSorting,
  })

  return <DataTable table={table} pageIndex={pageIndex} pageSize={pageSize} totalPages={totalPages} />
}

export default UsersTable
