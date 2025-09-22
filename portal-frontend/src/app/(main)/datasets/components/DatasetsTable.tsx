import {
  createColumnHelper,
  getCoreRowModel,
  getSortedRowModel,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'
import { Dispatch, SetStateAction } from 'react'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Badge } from '@/components/ui/badge'

import { Dataset } from '../page'

interface DatasetsTableProps {
  datasets: Dataset[]
  rowCount: number
  pageSize: number
  totalPages: number
  setPageSize: Dispatch<SetStateAction<number>>
  pageIndex: number
  setPageIndex: Dispatch<SetStateAction<number>>
  sorting: SortingState
  setSorting: Dispatch<SetStateAction<SortingState>>
}

const DatasetsTable = (props: DatasetsTableProps) => {
  const { datasets, rowCount, pageIndex, totalPages, setPageIndex, pageSize, setPageSize, sorting, setSorting } = props
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
                {value.title}
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
      const newPagination = typeof updater === 'function' ? updater({ pageIndex, pageSize }) : updater
      setPageIndex(newPagination.pageIndex)
      setPageSize(newPagination.pageSize)
    },
    onSortingChange: setSorting,
  })

  return <DataTable table={table} pageIndex={pageIndex} pageSize={pageSize} totalPages={totalPages} />
}

export default DatasetsTable
