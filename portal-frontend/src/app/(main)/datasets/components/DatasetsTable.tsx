import {
  createColumnHelper,
  getCoreRowModel,
  getSortedRowModel,
  SortDirection,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useLocale, useTranslations } from 'next-intl'
import { Dispatch, SetStateAction } from 'react'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import TablePagination from '@/components/table/table-pagination/TablePagination'
import { Badge } from '@/components/ui/badge'
import { ScrollArea, ScrollBar } from '@/components/ui/scroll-area'

import { Dataset } from '../page'

export const getAriaSort = (sorting: false | SortDirection) => {
  switch (sorting) {
    case 'asc':
      return 'ascending'
    case 'desc':
      return 'descending'
    default:
      return 'none'
  }
}

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
      header: ({ column }) => <SortableTableHeader column={column} title={t('header.name')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('dataSpace', {
      header: t('header.dataSpace'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('department', {
      header: t('header.department'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('creator', {
      header: t('header.creator'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('lastUpdated', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('header.lastUpdated')} />,
      cell: info => {
        const formattedDate = new Date(info.getValue()).toLocaleDateString('en-GB')
        return locale === 'de' ? formattedDate.replaceAll('/', '.') : formattedDate
      },
    }),
    columnHelper.accessor('status', {
      header: t('header.status'),
      cell: info => {
        const value = info.getValue()
        switch (value) {
          case 'open':
            return t(`values.status.${value}`)
          case 'closed':
            return `🔒 ${t(`values.status.${value}`)}`
          default:
            return ''
        }
      },
    }),
    columnHelper.accessor('releaseProcess', {
      header: t('header.releaseProcess'),
      cell: '',
    }),
    columnHelper.accessor('distribution', {
      header: t('header.distribution'),
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

  return (
    <div className="h-full w-full [--pagination-height:calc(--spacing(18))] [--pagination-padding:calc(--spacing(4))]">
      <ScrollArea className="h-full h-[calc(100%-var(--pagination-height))] w-full">
        <DataTable table={table} />
        <ScrollBar orientation="horizontal" />
      </ScrollArea>
      <TablePagination
        className="h-[calc(var(--pagination-height))]"
        pageIndex={pageIndex}
        pageSize={pageSize}
        totalPages={totalPages}
        table={table}
      />
    </div>
  )
}

export default DatasetsTable
