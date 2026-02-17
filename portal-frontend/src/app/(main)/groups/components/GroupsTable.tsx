import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { ExpanderCell } from '@/components/table/expander-cell/ExpanderCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Group } from '@/types/groups'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface GroupsTableProps extends TableProps<Group> {
  groups: Group[]
}

export const GroupsTable = (props: GroupsTableProps) => {
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
  const columnHelper = createColumnHelper<Group>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('parent', {
      header: 'parent',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('title', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('list.title')} />,
      cell: ({ row }: CellContext<Group, unknown>) => (
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
      header: t('list.users'),
      cell: info => info.getValue()?.length,
    }),
    columnHelper.accessor('contact', {
      header: t('list.contact'),
      cell: info => info.getValue()?.displayName,
    }),
    columnHelper.accessor('description', {
      header: t('list.description'),
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
        parent: false,
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    manualPagination: true,
    manualSorting: true,
    getSubRows: row => row.subgroups || [],
    getExpandedRowModel: getExpandedRowModel(),
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  const checkIfRowClickable = (row: Row<Group>) => !row.original.parent

  return (
    <DataTable
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      onRowClick={onRowClick}
      isLoading={isLoading}
      isRowClickable={checkIfRowClickable}
    />
  )
}
