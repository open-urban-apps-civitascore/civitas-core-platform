import { createColumnHelper, getCoreRowModel, getSortedRowModel, Row, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { UserGroupsListData } from '@/types/groups'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface GroupsTableProps extends TableProps<UserGroupsListData> {
  groups: UserGroupsListData[]
  onDelete: (id: string) => void
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
    onDelete,
    isLoading,
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
      cell: info => info.getValue(),
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
    }),
    columnHelper.accessor('contactUser', {
      header: t('groupsTab.contact'),
      cell: info => info.getValue()?.name || '-',
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
    {
      id: 'actions',
      cell: ({ row }: { row: Row<UserGroupsListData> }) => (
        <TableDropdownMenu
          menuItems={[
            {
              label: tCommon('actions.deleteItem', { item: tCommon('items.group') }),
              onClick: () => onDelete(row.original.id),
            },
          ]}
        />
      ),
    },
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
