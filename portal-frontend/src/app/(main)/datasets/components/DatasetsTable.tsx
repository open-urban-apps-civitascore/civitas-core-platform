import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Badge } from '@/components/ui/badge'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

import { Dataset } from '../page'

interface DatasetsTableProps extends TableProps<Dataset> {
  datasets: Dataset[]
}

const DatasetsTable = (props: DatasetsTableProps) => {
  const { datasets, rowCount, pageIndex, totalPages, pageSize, sorting, onPaginationChange, onSortingChange } = props
  const t = useTranslations('datasets')
  const locale = useLocale()
  const columnHelper = createColumnHelper<Dataset>()

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
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('dataSpace', {
      header: t('tableHeaders.dataSpace'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('department', {
      header: t('tableHeaders.department'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('creator', {
      header: t('tableHeaders.creator'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('lastUpdated', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.lastUpdated')} />,
      cell: info => {
        const formattedDate = new Date(info.getValue()).toLocaleDateString('en-GB')
        return locale === 'de' ? formattedDate.replaceAll('/', '.') : formattedDate
      },
    }),
    columnHelper.accessor('status', {
      header: t('tableHeaders.status'),
      cell: info => {
        const value = info.getValue()
        switch (value) {
          case 'open':
            return t(`tableValues.status.${value}`)
          case 'closed':
            return `🔒 ${t(`tableValues.status.${value}`)}`
          default:
            return ''
        }
      },
    }),
    columnHelper.accessor('releaseProcess', {
      header: t('tableHeaders.releaseProcess'),
      cell: '',
    }),
    columnHelper.accessor('distribution', {
      header: t('tableHeaders.distribution'),
      cell: info => {
        const value = info.getValue()
        if (value) {
          return (
            <>
              <Badge className="mr-3" variant="secondary">
                {value.format}
              </Badge>
              <a href={value.url} target="_blank" className="underline">
                {value.name}
              </a>
            </>
          )
        } else {
          return ''
        }
      },
    }),
  ]

  const table = useReactTable({
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

  return <DataTable table={table} pageIndex={pageIndex} pageSize={pageSize} totalPages={totalPages} />
}

export default DatasetsTable
