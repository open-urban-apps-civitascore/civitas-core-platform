'use client'

import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { formatDistanceStrict } from 'date-fns'
import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useLocale, useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { AppLocale, DATE_LOCALES } from '@/i18n/locales'
import { Datasource, DATASOURCE_STATUS_TYPES } from '@/types/datasources'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

export interface DatasourceTableProps extends TableProps<Datasource> {
  datasources: Datasource[]
  isLoading?: boolean
  isPaginationHidden?: boolean
}

export const DatasourceTable = (props: DatasourceTableProps) => {
  const {
    datasources,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    onPaginationChange,
    onSortingChange,
    isLoading,
    isPaginationHidden = false,
  } = props
  const tDatapools = useTranslations('datapools.overview.datasourcesTab')
  const locale = useLocale() as AppLocale
  const columnHelper = createColumnHelper<Datasource>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={tDatapools('tableHeaders.name')} />,
      cell: info =>
        info.getValue() ? <LinkCell href={`/datasources/${info.row.original.id}`}>{info.getValue()}</LinkCell> : '-',
      meta: {
        truncate: true,
        style: {
          width: '30%',
          minWidth: '200px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: tDatapools('tableHeaders.description'),
      cell: info => info.getValue() ?? '-',
      meta: {
        truncate: true,
        style: {
          width: '35%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('connectorType', {
      header: tDatapools('tableHeaders.connector'),
      cell: info => info.getValue() ?? '-',
      meta: {
        style: {
          width: '15%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('modifiedAt', {
      header: ({ column }) => <SortableTableHeader column={column} title={tDatapools('tableHeaders.lastActive')} />,
      cell: info => {
        const value = info.getValue()
        if (!value) return '-'
        return formatDistanceStrict(new Date(), new Date(value), { locale: DATE_LOCALES[locale] })
      },
      meta: {
        style: {
          width: '15%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('dataSourceStatus', {
      header: tDatapools('tableHeaders.status'),
      cell: info => {
        const status = info.getValue()
        return (
          <div className="flex items-center gap-2">
            {status === DATASOURCE_STATUS_TYPES.DRAFT ? (
              <CircleDashed className="text-muted-foreground" size={16} />
            ) : (
              <CircleCheckBig className="text-muted-foreground" size={16} />
            )}
            {tDatapools(`status.${status}`)}
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
  ]

  const table = useReactTable({
    getRowId: row => String(row.id),
    columns,
    data: datasources,
    rowCount,
    initialState: {
      columnVisibility: { id: false },
    },
    state: { pagination: { pageIndex, pageSize }, sorting },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize })),
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  return (
    <DataTable
      testId="datapoolDatasourcesTable"
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      isLoading={isLoading}
      isPaginationHidden={isPaginationHidden}
    />
  )
}
