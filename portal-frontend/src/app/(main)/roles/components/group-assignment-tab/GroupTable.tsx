import { createColumnHelper, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { AssignmentScope } from '@/types/assignments'
import { Group } from '@/types/groups'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

export interface GroupTableRow extends Group {
  scopeType?: AssignmentScope
}

interface GroupTableProps extends TableProps<GroupTableRow> {
  groups: GroupTableRow[]
  isEditMode?: boolean
  onRemoveGroup?: (group: GroupTableRow) => void
}

export const GroupTable = (props: GroupTableProps) => {
  const {
    groups,
    isLoading,
    sorting,
    pageIndex,
    pageSize,
    totalPages,
    rowSelection,
    rowCount,
    onRowClick,
    onPaginationChange,
    onSortingChange,
    isEditMode,
    onRemoveGroup,
  } = props
  const t = useTranslations('roles.groupAssignmentTab')

  const columnHelper = createColumnHelper<GroupTableRow>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'ID',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => {
        return <SortableTableHeader column={column} title={t('tableHeaders.name')} />
      },
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '20%',
        },
      },
    }),
    columnHelper.accessor('members', {
      header: () => t('tableHeaders.usersCount'),
      cell: info => info.getValue()?.length || 0,
      meta: {
        style: {
          width: '10%',
        },
      },
    }),
    columnHelper.accessor('contactUser', {
      header: () => t('tableHeaders.contact'),
      cell: info => info.getValue()?.name ?? '',
      meta: {
        style: {
          width: '15%',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: () => t('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '30%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('scopeType', {
      header: () => t('tableHeaders.scope'),
      cell: info => {
        const scopeType = info.getValue()
        return scopeType ? t(`scopeLabels.${scopeType}`) : ''
      },
      meta: {
        style: {
          width: '15%',
        },
      },
    }),
    ...(isEditMode && onRemoveGroup
      ? [
          columnHelper.display({
            id: 'actions',
            header: () => null,
            cell: ({ row }) => (
              <TableDropdownMenu
                menuItems={[
                  {
                    label: t('removeAssignment'),
                    onClick: () => onRemoveGroup(row.original),
                  },
                ]}
              />
            ),
            meta: {
              style: {
                width: '60px',
              },
            },
          }),
        ]
      : []),
  ]

  const table = useReactTable({
    columns: columns,
    data: groups,
    getCoreRowModel: getCoreRowModel(),
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    rowCount,
    manualPagination: true,
    manualSorting: true,
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
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
      isLoading={isLoading}
      onRowClick={onRowClick}
    />
  )
}
