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
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

type Status = 'open' | 'closed' | null
type Creator = { id: string; firstName: string; lastName: string }
type Distribution = {
  format: string
  title: string
  url: string
}
type Category = { id: string; title: string }

type Dataset = {
  id: number
  name: string
  dataRoom: string
  department: string
  creator: string[]
  lastUpdated: string
  status: Status
  approvalProcess: null
  distribution: Distribution | null
}

type DataSetResponse = {
  id: 1
  title: string
  creator: Creator[]
  issued: string
  modified: string
  status: Status
  distribution: (Distribution & { id: string }) | null
  catalog: string[]
  series: Category
  department: Category
}

const mapDatasets = (datasets: DataSetResponse[]): Dataset[] =>
  datasets.map(dataset => ({
    id: dataset.id,
    name: dataset.title,
    dataRoom: dataset.series.title,
    department: dataset.department.title,
    creator: dataset.creator.map(creator => `${creator.firstName} ${creator.lastName}`),
    lastUpdated: dataset.modified,
    status: dataset.status,
    approvalProcess: null,
    distribution: dataset.distribution
      ? {
          format: dataset.distribution?.format,
          title: dataset.distribution?.title,
          url: dataset.distribution?.url,
        }
      : null,
  }))

const DatasetsTable = () => {
  const t = useTranslations('datasets')
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const columnHelper = createColumnHelper<Dataset>()

  useEffect(() => {
    const getData = async () => {
      const data = await fetch(
        `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}/datasets`,
      )
      const datasetsResponse: DataSetResponse[] = await data.json()
      const datasets = mapDatasets(datasetsResponse)
      setDatasets(datasets)
    }
    getData()
  }, [])

  const columnHeader = <TValue,>(title: string) => {
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
      header: columnHeader('id'),
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: columnHeader('name'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('dataRoom', {
      header: columnHeader('dataRoom'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('department', {
      header: columnHeader('department'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('creator', {
      header: columnHeader('creator'),
      cell: info => info.getValue(),
    }),
    columnHelper.accessor('lastUpdated', {
      header: columnHeader('lastUpdated'),
      cell: info => new Date(info.getValue()).toLocaleDateString(),
    }),
    columnHelper.accessor('status', {
      header: columnHeader('status'),
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
    columnHelper.accessor('approvalProcess', {
      header: columnHeader('approvalProcess'),
      cell: '',
    }),
    columnHelper.accessor('distribution', {
      header: columnHeader('distribution'),
      cell: info => {
        const value = info.getValue()
        if (value) {
          return (
            <a href={value.url} target="_blank">
              <Badge className="mr-3" variant="secondary">
                {value.format}
              </Badge>
              {value.title}
            </a>
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
    <Table className="w-full">
      <TableCaption></TableCaption>
      <TableHeader>
        {table.getHeaderGroups().map(group => (
          <TableRow key={group.id}>
            {group.headers.map(header => (
              <TableHead key={header.id} className="w-[100px] text-primary-light">
                {flexRender(header.column.columnDef.header, header.getContext())}
              </TableHead>
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {table.getRowModel().rows.map(row => (
          <TableRow key={row.id}>
            {row.getVisibleCells().map(cell => (
              <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export default DatasetsTable
