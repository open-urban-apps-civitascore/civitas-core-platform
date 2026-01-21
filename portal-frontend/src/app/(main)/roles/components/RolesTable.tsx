import { createColumnHelper, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'

import { Role, ROLE_ORIGINS } from '../../../../../types/roles'
import { TableProps } from '../../../../../types/table'

interface RolesTableProps extends TableProps<Role> {
  roles: Role[]
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
    onRowClick,
    onPaginationChange,
    onSortingChange,
  } = props
  const t = useTranslations('roles')
  const locale = useLocale()

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
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '17.5%',
        },
      },
    }),

    columnHelper.accessor('description', {
      header: () => t('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '25%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('groups', {
      header: () => t('tableHeaders.groups'),
      cell: info => info.getValue()?.length,
      meta: {
        style: {
          width: '10%',
        },
      },
    }),
    columnHelper.accessor('users', {
      header: () => t('tableHeaders.user'),
      cell: info => info.getValue()?.length,
      meta: {
        style: {
          width: '10%',
        },
      },
    }),
    columnHelper.accessor('lastUpdated', {
      header: () => t('tableHeaders.lastUpdated'),
      cell: info => formatDate(info.getValue(), locale),
      meta: {
        style: {
          width: '12.5%',
          textAlign: 'center',
        },
      },
    }),
    columnHelper.accessor('updatedBy', {
      header: () => t('tableHeaders.updatedBy'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '12.5%',
        },
      },
    }),
    columnHelper.accessor('roleOrigin', {
      header: () => t('tableHeaders.roleOrigin'),
      cell: info => (info.getValue() === ROLE_ORIGINS.DEFAULT ? t('defaultRole') : t('customRole')),
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
