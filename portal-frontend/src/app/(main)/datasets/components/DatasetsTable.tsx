import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  HeaderContext,
  useReactTable,
} from '@tanstack/react-table'
import { ArrowUpDown } from 'lucide-react'
import { useLocale, useTranslations } from 'next-intl'
import { Dispatch, SetStateAction } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

import { Dataset } from '../page'
import TablePagination from './TablePagination'

interface DatasetsTableProps {
  datasets: Dataset[]
  rowCount: number
  pageSize: number
  setPageSize: Dispatch<SetStateAction<number>>
  pageIndex: number
  setPageIndex: Dispatch<SetStateAction<number>>
}

const DatasetsTable = (props: DatasetsTableProps) => {
  const { datasets, rowCount, pageIndex, setPageIndex, pageSize, setPageSize } = props
  const t = useTranslations('datasets')
  const locale = useLocale()
  const columnHelper = createColumnHelper<Dataset>()

  const getColumnHeader = <TValue,>(title: string) => {
    const Header = (ctx: HeaderContext<Dataset, TValue>) => {
      const { column } = ctx
      return (
        <>
          {title === 'id' ? 'id' : t(`header.${title}`)}
          <Button
            className="hover:bg-transparent hover:cursor-pointer"
            variant="ghost"
            onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
          >
            <ArrowUpDown />
          </Button>
        </>
      )
    }
    return Header
  }

  const columns = [
    columnHelper.accessor('id', {
      header: getColumnHeader('id'),
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: getColumnHeader('name'),
      cell: info => info.getValue(),
      meta: { flex: 2 },
    }),
    columnHelper.accessor('dataSpace', {
      header: getColumnHeader('dataSpace'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('department', {
      header: getColumnHeader('department'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('creator', {
      header: getColumnHeader('creator'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('lastUpdated', {
      header: getColumnHeader('lastUpdated'),
      cell: info => {
        const formattedDate = new Date(info.getValue()).toLocaleDateString('en-GB')
        return locale === 'de' ? formattedDate.replaceAll('/', '.') : formattedDate
      },
    }),
    columnHelper.accessor('status', {
      header: getColumnHeader('status'),
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
      header: getColumnHeader('releaseProcess'),
      cell: '',
    }),
    columnHelper.accessor('distribution', {
      header: getColumnHeader('distribution'),
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
    state: { pagination: { pageIndex, pageSize } },
    onPaginationChange: updater => {
      const newPagination = typeof updater === 'function' ? updater({ pageIndex, pageSize }) : updater
      setPageIndex(newPagination.pageIndex)
      setPageSize(newPagination.pageSize)
    },
  })

  return (
    <div className=" flex-col h-full [--pagination-height:calc(--spacing(18))] [--pagination-padding:calc(--spacing(4))]">
      <ScrollArea className="h-full h-[calc(100%-var(--pagination-height))]">
        <Table className="w-full">
          <TableHeader>
            {table.getHeaderGroups().map(group => (
              <TableRow key={group.id}>
                {group.headers.map(header => (
                  <TableHead key={header.id} className="text-primary-light">
                    {flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.map(row => (
              <TableRow className="h-16" key={row.id}>
                {row.getVisibleCells().map(cell => (
                  <TableCell className="whitespace-normal" key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ScrollArea>
      <TablePagination
        className="h-[calc(var(--pagination-height))]"
        pageIndex={pageIndex}
        pageSize={pageSize}
        rowCount={rowCount}
        table={table}
      />
    </div>
  )
}

export default DatasetsTable
