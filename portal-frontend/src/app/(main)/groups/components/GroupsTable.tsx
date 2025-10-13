import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { ExpanderCell } from '@/components/table/expander-cell/ExpanderCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { GroupResponse } from '@/types/groups'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface GroupsTableProps extends TableProps<GroupResponse> {
  groups: GroupResponse[]
}

const GroupsTable = (props: GroupsTableProps) => {
  const {
    groups,
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
  const t = useTranslations('groups')
  const columnHelper = createColumnHelper<GroupResponse>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('title', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('info.title')} />,
      cell: ({ row }: CellContext<GroupResponse, unknown>) => (
        <ExpanderCell row={row} value={row.original.title} className="font-medium" />
      ),
      meta: {
        style: {
          minWidth: '200px',
          color: 'var(--foreground)',
        },
      },
    }),
    columnHelper.accessor('users', {
      header: t('info.users'),
      cell: info => info.getValue().length,
    }),
    columnHelper.accessor('contact', {
      header: t('info.contact'),
      cell: info => info.getValue().displayName,
    }),
    columnHelper.accessor('description', {
      header: t('info.description'),
      cell: info => info.getValue(),
      meta: {
        style: {
          whiteSpace: 'nowrap',
          maxWidth: '300px',
          textOverflow: 'ellipsis',
          overflow: 'hidden',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: groups,
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    manualPagination: true,
    manualSorting: true,
    getSubRows: row => row.children || [],
    getExpandedRowModel: getExpandedRowModel(),
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

export default GroupsTable
