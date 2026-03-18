import {
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Group } from '@/types/groups'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface GroupsTableProps extends TableProps<Group> {
  groups: Group[]
  onDeleteGroupClick?: (groupId: string) => void
  isLinkDisabled?: boolean
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
    onPaginationChange,
    onSortingChange,
    onDeleteGroupClick,
    isLoading,
    isLinkDisabled,
  } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const columnHelper = createColumnHelper<Group>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('list.title')} />,
      cell: ({ row }) => (
        <LinkCell href={`/groups/${row.id}`} isDisabled={isLinkDisabled}>
          {row.original.name}
        </LinkCell>
      ),
      meta: {
        style: {
          minWidth: '150px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('members', {
      header: t('list.users'),
      cell: info => info.getValue()?.length,
    }),
    columnHelper.accessor('contactUser', {
      header: t('list.contact'),
      cell: info => info.getValue()?.name,
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
    ...(onDeleteGroupClick
      ? [
          {
            id: 'actions',
            cell: ({ row }: { row: Row<Group> }) => (
              <TableDropdownMenu
                menuItems={[
                  {
                    label: tCommon('actions.removeItem', { item: tCommon('items.group') }),
                    onClick: () => onDeleteGroupClick(row.id),
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
    getExpandedRowModel: getExpandedRowModel(),
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  return (
    <DataTable table={table} pageIndex={pageIndex} pageSize={pageSize} totalPages={totalPages} isLoading={isLoading} />
  )
}
