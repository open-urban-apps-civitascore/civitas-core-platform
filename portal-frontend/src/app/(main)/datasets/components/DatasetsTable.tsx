'use client'

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

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

import { Dataset } from '../page'

interface DatasetsTableProps {
  datasets: Dataset[]
}

const DatasetsTable = (props: DatasetsTableProps) => {
  const { datasets } = props
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
    columnHelper.accessor('dataRoom', {
      header: getColumnHeader('dataRoom'),
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
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
  })

  return (
    <Table className="w-full overflow-x-auto">
      <TableCaption></TableCaption>
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
  )
}

export default DatasetsTable
