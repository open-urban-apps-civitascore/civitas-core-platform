import { createColumnHelper, getCoreRowModel, getSortedRowModel, Row, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { UserGroupsListData } from '@/types/groups'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface GroupsTableProps extends TableProps<UserGroupsListData> {
  groups: UserGroupsListData[]
  onRemoveGroupClick: (id: string) => void
  isReadOnly: boolean
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
    onRemoveGroupClick,
    isLoading,
    isReadOnly,
  } = props
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const columnHelper = createColumnHelper<UserGroupsListData>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('groupsTab.name')} />,
      cell: ({ row }) => <LinkCell href={`/groups/${row.id}`}>{row.original.name}</LinkCell>,
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('membersCount', {
      header: t('groupsTab.membersCount'),
      cell: info => info.getValue() || 0,
      meta: {
        style: {
          width: '22.22%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('contactUser', {
      header: t('groupsTab.contact'),
      cell: info => info.getValue()?.name || '-',
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: t('groupsTab.description'),
      cell: info => info.getValue() || '-',
      meta: {
        style: {
          whiteSpace: 'nowrap',
          maxWidth: '300px',
          textOverflow: 'ellipsis',
          overflow: 'hidden',
        },
      },
    }),
    ...(!isReadOnly
      ? [
          {
            id: 'actions',
            cell: ({ row }: { row: Row<UserGroupsListData> }) => (
              <TableDropdownMenu
                menuItems={[
                  {
                    label: tCommon('actions.removeItem', { item: tCommon('items.group') }),
                    onClick: () => onRemoveGroupClick(row.original.id),
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
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    manualPagination: true,
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
      hasCard={false}
    />
  )
}

export default GroupsTable
