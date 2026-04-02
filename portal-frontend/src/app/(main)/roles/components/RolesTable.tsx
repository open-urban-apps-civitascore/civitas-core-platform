import { createColumnHelper, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Role } from '@/types/roles'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface RolesTableProps extends TableProps<Role> {
  roles: Role[]
  selectedRoleType?: string
}

export const RolesTable = (props: RolesTableProps) => {
  const {
    roles,
    isLoading,
    sorting,
    pageIndex,
    pageSize,
    totalPages,
    rowSelection,
    rowCount,
    onPaginationChange,
    onSortingChange,
    selectedRoleType,
  } = props
  const t = useTranslations('roles')

  const columnHelper = createColumnHelper<Role>()

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
      cell: ({ row }) => (
        <LinkCell href={`/roles/${row.original.id}${selectedRoleType ? `?tab=${selectedRoleType}` : ''}`}>
          {row.original.name}
        </LinkCell>
      ),
      meta: {
        truncate: true,
        style: {
          width: '17.5%',
        },
      },
    }),

    columnHelper.accessor('description', {
      header: () => t('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        truncate: true,
        style: {
          width: '25%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('groupCount', {
      header: () => t('tableHeaders.groups'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '10%',
        },
      },
    }),
    columnHelper.accessor('userCount', {
      header: () => t('tableHeaders.user'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '10%',
        },
      },
    }),
    columnHelper.accessor('modifiedBy', {
      header: () => t('tableHeaders.updatedBy'),
      cell: info => info.getValue()?.name ?? null,
      meta: {
        style: {
          width: '12.5%',
        },
      },
    }),
    columnHelper.accessor('readonly', {
      header: () => t('tableHeaders.roleOrigin'),
      cell: info => (info.getValue() ? t('defaultRole') : t('customRole')),
      meta: {
        style: {
          width: '12.5%',
        },
      },
    }),
  ]

  const table = useReactTable({
    columns: columns,
    data: roles,
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
    <DataTable table={table} pageIndex={pageIndex} pageSize={pageSize} totalPages={totalPages} isLoading={isLoading} />
  )
}
