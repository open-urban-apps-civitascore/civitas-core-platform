import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { ContactCell } from '@/components/table/contact-cell/ContactCell'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { BaseDatasetTableData } from '@/types/datasets'
import { TableProps } from '@/types/table'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'

export interface DatasetTableProps extends TableProps<BaseDatasetTableData> {
  datasets: BaseDatasetTableData[]
  isLoading: boolean
}

export const DatasetTable = (props: DatasetTableProps) => {
  const {
    datasets,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onPaginationChange,
    onSortingChange,
    isLoading,
  } = props

  const t = useTranslations('datapools.overview.datasetsTab')

  const locale = useLocale()

  const columnHelper = createColumnHelper<BaseDatasetTableData>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: info =>
        info.getValue() ? <LinkCell href={`/datasets/${info.row.original.id}`}>{info.getValue()}</LinkCell> : '-',

      meta: {
        truncate: true,
        style: {
          width: '35%',
          minWidth: '200px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('createdBy', {
      header: t('tableHeaders.contact'),
      cell: info => {
        return <div>{info.getValue() ? <ContactCell user={info.getValue() ?? null} /> : t('noContact')}</div>
      },
      meta: {
        style: {
          width: '20%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('modifiedAt', {
      header: t('tableHeaders.modifiedAt'),
      cell: info => {
        return <span>{formatDate(info.getValue(), locale)}</span>
      },
      meta: {
        style: {
          width: '20%',
          minWidth: '150px',
        },
      },
    }),
    columnHelper.accessor('dataSetStatus', {
      header: t('tableHeaders.status'),
      cell: info => info.getValue(),
      meta: {
        truncate: true,
        style: {
          width: '15%',
          minWidth: '200px',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => String(row.id),
    columns: columns,
    data: datasets,
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
      testId="datasetsTable"
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      isLoading={isLoading}
    />
  )
}
