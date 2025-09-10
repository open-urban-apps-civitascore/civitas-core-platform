'use client'

import { ColumnDef, flexRender, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { ArrowUpDown } from 'lucide-react'
import { useLocale, useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { BaseBadge } from '@/components/base/BaseBadge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

type Status = 'open' | 'limited' | 'closed'

type Dataset = {
  id: number
  name: string
  dataType: string
  dataSteward: string
  lastUpdated: string
  status: Status
  distribution: string
}

const StatusBadge = ({ status }: { status: Status }) => {
  switch (status) {
    case 'open':
      return <BaseBadge variant="success">{status}</BaseBadge>
    case 'limited':
      return <BaseBadge variant="warn">{status}</BaseBadge>
    case 'closed':
      return <BaseBadge variant="error">{status}</BaseBadge>
  }
}

const DatasetsTable = () => {
  const t = useTranslations('datasets')
  const locale = useLocale()
  const [datasets, setDatasets] = useState<Dataset[]>([])

  useEffect(() => {
    const getData = async () => {
      const data = await fetch('/datasets.json')
      const datasets: Dataset[] = await data.json()
      setDatasets(datasets)
    }
    getData()
  }, [])
  const columns: ColumnDef<Dataset>[] =
    datasets.length > 0
      ? Object.keys(datasets[0]).flatMap(key => ({
          accessorKey: key,
          header: ({ column }) => {
            return (
              <>
                {t(key)}
                <Button
                  className="hover:bg-transparent hover:cursor-pointer"
                  variant="ghost"
                  onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
                >
                  <ArrowUpDown />
                </Button>
              </>
            )
          },
          ...((key === 'name' || key === 'dataSteward') && {
            sortingFn: (a, b) => a.original.name.localeCompare(b.original.name, locale),
          }),
        }))
      : []
  const table = useReactTable({
    columns,
    data: datasets,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    state: {
      columnVisibility: { id: false },
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
            <TableCell className="font-medium">{row.getValue('name')}</TableCell>
            <TableCell>{row.getValue('dataType')}</TableCell>
            <TableCell>{row.getValue('dataSteward')}</TableCell>
            <TableCell>{new Date(row.getValue('lastUpdated')).toLocaleDateString()}</TableCell>
            <TableCell>
              <StatusBadge status={row.getValue('status') as Status} />
            </TableCell>
            <TableCell>{row.getValue('distribution')}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export default DatasetsTable
