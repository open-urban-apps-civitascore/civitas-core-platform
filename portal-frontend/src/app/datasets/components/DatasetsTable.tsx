'use client'

import {
  ColumnDef,
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { ArrowUpDown } from 'lucide-react'
import { useLocale, useTranslations } from 'next-intl'

import { BaseBadge } from '@/components/base/BaseBadge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

type DatasetsData = (typeof DATASETS_DATA)[number]

const DATASETS_DATA = [
  {
    id: 1,
    name: 'Straßenbeleuchtung',
    dataType: 'json',
    dataSteward: 'Jonas Becker',
    lastUpdated: '2025-08-25T21:15:00Z',
    status: 'open',
    distribution: 'Masterportal',
  },
  {
    id: 2,
    name: 'Parkplatzbelegung',
    dataType: 'csv',
    dataSteward: 'Lara König',
    lastUpdated: '2025-09-01T08:00:00Z',
    status: 'open',
    distribution: 'Open Data Portal',
  },
  {
    id: 3,
    name: 'Luftqualität',
    dataType: 'csv',
    dataSteward: 'Mehmet Yilmaz',
    lastUpdated: '2025-08-20T12:30:00Z',
    status: 'closed',
    distribution: 'Superset',
  },
  {
    id: 4,
    name: 'Wasserverbrauch',
    dataType: 'xlsx',
    dataSteward: 'Elena Wagner',
    lastUpdated: '2025-07-14T09:10:00Z',
    status: 'limited',
    distribution: 'Masterportal',
  },
  {
    id: 5,
    name: 'Energieverbrauch öffentlicher Gebäude',
    dataType: 'json',
    dataSteward: 'Nico Braun',
    lastUpdated: '2025-06-28T18:00:00Z',
    status: 'open',
    distribution: 'Open Data Portal',
  },
  {
    id: 6,
    name: 'Echtzeit-Positionen Busse und Straßenbahnen',
    dataType: 'kml',
    dataSteward: 'Clara Fischer',
    lastUpdated: '2025-08-30T11:45:00Z',
    status: 'open',
    distribution: 'Superset',
  },
  {
    id: 7,
    name: 'Verkehrsfluss / Staudaten',
    dataType: 'geojson',
    dataSteward: 'David Weber',
    lastUpdated: '2025-09-03T07:30:00Z',
    status: 'open',
    distribution: 'Open Data Portal',
  },
  {
    id: 8,
    name: 'Lärmbelastung',
    dataType: 'csv',
    dataSteward: 'Mara Hoffmann',
    lastUpdated: '2025-08-12T15:20:00Z',
    status: 'limited',
    distribution: 'Masterportal',
  },
  {
    id: 9,
    name: 'Wetterstationen',
    dataType: 'json',
    dataSteward: 'Fatima Özdemir',
    lastUpdated: '2025-09-05T05:00:00Z',
    status: 'open',
    distribution: 'Superset',
  },
  {
    id: 10,
    name: 'Öffentliche E-Ladesäulen',
    dataType: 'csv',
    dataSteward: 'Thomas Klein',
    lastUpdated: '2025-08-27T20:00:00Z',
    status: 'closed',
    distribution: 'Open Data Portal',
  },
]

type Status = 'open' | 'limited' | 'closed'

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
  const columns: ColumnDef<DatasetsData>[] = Object.keys(DATASETS_DATA[0]).flatMap((key, index) =>
    index === 0
      ? []
      : {
          accessorKey: key,
          header: ({ column }) => {
            return (
              <Button variant="ghost" onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}>
                {t(key)}
                <ArrowUpDown />
              </Button>
            )
          },
          ...((key === 'name' || key === 'dataSteward') && {
            sortingFn: (a, b) => a.original.name.localeCompare(b.original.name, locale),
          }),
        },
  )
  const columnHelper = createColumnHelper<DatasetsData>()
  const table = useReactTable({
    columns,
    data: DATASETS_DATA,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
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
