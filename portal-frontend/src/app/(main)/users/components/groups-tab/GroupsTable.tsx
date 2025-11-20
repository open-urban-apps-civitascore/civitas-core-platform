import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { BasicTooltip } from '@/components/tooltip/Tooltip'
import { Badge } from '@/components/ui/badge'
import { UserGroupsListData } from '@/types/groups'
import { TableProps } from '@/types/table'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'

interface GroupsTableProps extends TableProps<UserGroupsListData> {
  groups: UserGroupsListData[]
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
  const t = useTranslations('users')
  const locale = useLocale()
  const columnHelper = createColumnHelper<UserGroupsListData>()

  console.log(groups)
  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('title', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('groupsTab.name')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('memberSince', {
      header: t('groupsTab.memberSince'),
      cell: info => formatDate(info.getValue(), locale),
    }),
    columnHelper.accessor('contact', {
      header: t('groupsTab.contact'),
      cell: info => info.getValue()?.displayName || '-',
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
    columnHelper.accessor('roles', {
      header: t('groupsTab.roles'),
      cell: info => {
        const roles = info.getValue()
        if (!roles || roles.length === 0) return '-'
        const tooltipContent = (
          <div className="flex flex-wrap gap-2">
            {' '}
            {roles.slice(2, roles.length).map(role => (
              <Badge key={role} variant="secondary">
                {role}
              </Badge>
            ))}
          </div>
        )
        const badges = (
          <>
            {roles.slice(0, 2).map(role => (
              <Badge key={role} variant="secondary">
                {role}
              </Badge>
            ))}
            {roles.length > 2 && (
              <BasicTooltip tooltipContent={tooltipContent}>
                <Badge variant="outline">+{roles.length - 2}</Badge>
              </BasicTooltip>
            )}
          </>
        )
        return <div className="flex flex-wrap gap-1">{badges}</div>
      },
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
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
