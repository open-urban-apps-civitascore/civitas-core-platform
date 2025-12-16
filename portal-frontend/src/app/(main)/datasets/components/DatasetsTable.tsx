import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { cn } from '@/lib/utils'
import { DatasetTableData } from '@/types/datasets'
import { TableProps } from '@/types/table'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'
import { LockKeyhole, LockOpen } from 'lucide-react'

interface DatasetsTableProps extends TableProps<DatasetTableData> {
  datasets: DatasetTableData[]
}

const DatasetsTable = (props: DatasetsTableProps) => {
  const {
    datasets,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    onPaginationChange,
    onSortingChange,
    onRowClick,
  } = props
  const t = useTranslations('datasets')
  const locale = useLocale()
  const columnHelper = createColumnHelper<DatasetTableData>()

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
          minWidth: '250px',
        },
      },
    }),
    columnHelper.accessor('dataspace', {
      header: t('tableHeaders.dataSpace'),
      cell: info => info.getValue()?.name || '-',
      meta: {
        style: {
          minWidth: '150px',
        },
      },
    }),
    columnHelper.accessor('contact', {
      header: t('tableHeaders.contact'),
      cell: info => `${info.getValue().firstName} ${info.getValue().lastName}`,
      meta: {
        style: {
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('lastUpdated', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.lastUpdated')} />,
      cell: info => formatDate(info.getValue(), locale),
      meta: {
        style: {
          width: '150px',
        },
      },
    }),
    columnHelper.accessor('access', {
      header: t('tableHeaders.access'),
      cell: info => (
        <div
          className={cn(
            'flex justify-center items-center rounded-md w-9 h-9 border-solid border-1 border-border',
            info.getValue() ? 'bg-primary/20' : 'bg-secondary',
          )}
        >
          {info.getValue() ? (
            <LockOpen className="h-4 w-4 text-primary" />
          ) : (
            <LockKeyhole className="h-4 w-4 text-muted-foreground" />
          )}
        </div>
      ),
      meta: {
        style: {
          width: '100px',
        },
      },
    }),
    columnHelper.accessor('status', {
      header: t('tableHeaders.status'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '150px',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: datasets,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    manualPagination: true,
    manualSorting: true,
    state: { pagination: { pageIndex, pageSize }, sorting },
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
      onRowClick={onRowClick}
    />
  )
}

export default DatasetsTable
