import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Badge } from '@/components/ui/badge'
import { Datasource } from '@/types/datasources'
import { TableProps } from '@/types/table'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'
import { Row } from '@tanstack/react-table'
import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'

interface DatasourcesTableProps extends TableProps<Datasource> {
  datasources: Datasource[]
  onDelete?: (id: string) => void
}

export const DatasourcesTable = (props: DatasourcesTableProps) => {
  const {
    datasources,
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
  } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const locale = useLocale()
  const columnHelper = createColumnHelper<Datasource>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '20%',
          minWidth: '200px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: t('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '25%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('connector', {
      header: t('tableHeaders.connector'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('connection', {
      header: t('tableHeaders.connection'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('lastActive', {
      header: t('tableHeaders.lastActive'),
      cell: info => formatDate(info.getValue(), locale),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('tags', {
      header: t('tableHeaders.tags'),
      cell: info => info.getValue().map(value => <Badge key={value}>{value}</Badge>),
      meta: {
        style: {
          width: '15%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('status', {
      header: t('tableHeaders.status'),
      cell: info => t(`status.${info.getValue()}`),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    {
      id: 'actions',
      cell: ({ row }: { row: Row<Datasource> }) => (
        <TableDropdownMenu
          menuItems={[{ label: tCommon('actions.delete'), onClick: () => onDelete?.(row.original.id) }]}
        />
      ),
    },
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: datasources,
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  return (
    <DataTable
      testId="datasourcesTable"
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      onRowClick={onRowClick}
    />
  )
}
