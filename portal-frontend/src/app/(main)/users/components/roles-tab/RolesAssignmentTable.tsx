'use client'

import { createColumnHelper, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { AssignmentSummary } from '@/app/services/api/assignments/clientRequests'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface RolesAssignmentTableProps extends TableProps<AssignmentSummary> {
  assignments: AssignmentSummary[]
  isPlatformWide: boolean
}

export const RolesAssignmentTable = (props: RolesAssignmentTableProps) => {
  const {
    assignments,
    rowCount,
    pageIndex,
    pageSize,
    totalPages,
    sorting,
    isLoading,
    isPlatformWide,
    onPaginationChange,
    onSortingChange,
  } = props

  const t = useTranslations('users.rolesTab')

  const columnHelper = createColumnHelper<AssignmentSummary>()

  const columns = [
    columnHelper.accessor(row => row.role.name, {
      id: 'role.name',
      header: ({ column }) => <SortableTableHeader column={column} title={t('columns.name')} />,
      cell: ({ row }) => <LinkCell href={`/roles/${row.original.role.id}`}>{row.original.role.name}</LinkCell>,
      meta: {
        style: {
          width: '20%',
          minWidth: '150px',
        },
      },
    }),
    columnHelper.accessor(row => row.role.description, {
      id: 'role.description',
      header: t('columns.description'),
      cell: info => info.getValue() || '-',
      meta: {
        style: {
          width: '30%',
          minWidth: '200px',
          whiteSpace: 'nowrap',
          maxWidth: '300px',
          textOverflow: 'ellipsis',
          overflow: 'hidden',
        },
      },
    }),
    columnHelper.accessor(row => row.role.roleType, {
      id: 'role.roleType',
      header: ({ column }) => <SortableTableHeader column={column} title={t('columns.role')} />,
      cell: info => t(`roleTypes.${info.getValue()}`),
      meta: {
        style: {
          width: '15%',
          minWidth: '120px',
        },
      },
    }),
    columnHelper.accessor(row => row.role.readonly, {
      id: 'role.readonly',
      header: ({ column }) => <SortableTableHeader column={column} title={t('columns.type')} />,
      cell: info => (info.getValue() ? t('types.standard') : t('types.custom')),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor(row => (isPlatformWide ? t('scopes.platform') : (row.scope?.name ?? '-')), {
      id: 'scope',
      header: ({ column }) => <SortableTableHeader column={column} title={t('columns.scope')} />,
      meta: {
        style: {
          width: '25%',
          minWidth: '150px',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns,
    data: assignments,
    rowCount,
    state: { pagination: { pageIndex, pageSize }, sorting },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
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
      hasCard
    />
  )
}
