import { createColumnHelper, getCoreRowModel, getSortedRowModel, Row, useReactTable } from '@tanstack/react-table'
import { formatDistanceStrict } from 'date-fns'
import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useLocale, useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { AppLocale, DATE_LOCALES } from '@/i18n/locales'
import { Datasource, DATASOURCE_STATUS_TYPES } from '@/types/datasources'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

export interface DatasourcesTableProps extends TableProps<Datasource> {
  datasources: Datasource[]
  onDelete?: (datasource: Datasource) => void
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
  const locale = useLocale() as AppLocale
  const columnHelper = createColumnHelper<Datasource>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: info =>
        info.getValue() ? <LinkCell href={`datasources/${info.row.original.id}`}>{info.getValue()}</LinkCell> : '-',

      meta: {
        truncate: true,
        style: {
          width: '25%',
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
        truncate: true,
        style: {
          width: '30%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('connectorType', {
      header: t('tableHeaders.connector'),
      cell: info => info.getValue() ?? '-',
      meta: {
        style: {
          width: '15%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('modifiedAt', {
      header: t('tableHeaders.lastActive'),
      cell: info => {
        const value = info.getValue()
        if (!value) return '-'
        const interval = formatDistanceStrict(new Date(), new Date(value), { locale: DATE_LOCALES[locale] })
        return interval
      },
      meta: {
        style: {
          width: '15%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('dataSourceStatus', {
      header: t('tableHeaders.status'),
      cell: info => {
        const status = info.getValue()
        return (
          <div className="flex items-center gap-2">
            {status === DATASOURCE_STATUS_TYPES.DRAFT ? (
              <CircleDashed className="text-muted-foreground" size={16} />
            ) : (
              <CircleCheckBig className="text-muted-foreground" size={16} />
            )}
            {t(`status.${info.getValue()}`)}
          </div>
        )
      },
      meta: {
        style: {
          width: '15%',
          minWidth: '120px',
        },
      },
    }),
    {
      id: 'actions',
      cell: ({ row }: { row: Row<Datasource> }) => (
        <TableDropdownMenu
          menuItems={[{ label: tCommon('actions.delete'), onClick: () => onDelete?.(row.original) }]}
        />
      ),
    },
  ]

  const table = useReactTable({
    getRowId: row => String(row.id),
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
