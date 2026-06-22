import {
  createColumnHelper,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  PaginationState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import React, { useState } from 'react'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { ContactCell } from '@/components/table/contact-cell/ContactCell'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Datapool } from '@/types/datapools'

export interface DatapoolsTableProps {
  datapools: Datapool[]
  isLoading: boolean
  onDeleteClick?: (datapoolId: Datapool['id']) => void
  tableAction?: React.ReactNode
}

type DatapoolBaseTableData = Pick<Datapool, 'id' | 'name' | 'description' | 'contactPerson'>

export const DatapoolsTable = (props: DatapoolsTableProps) => {
  const { datapools, isLoading, onDeleteClick, tableAction } = props

  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: 10 })
  const [sorting, setSorting] = useState<SortingState>([])

  const t = useTranslations('datasources.datapoolsTab.table')
  const tCommon = useTranslations('common')

  const columnHelper = createColumnHelper<DatapoolBaseTableData>()

  const columns = [
    columnHelper.accessor('id', {
      header: '',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: info =>
        info.getValue() ? <LinkCell href={`/datapools/${info.row.original.id}`}>{info.getValue()}</LinkCell> : '-',

      meta: {
        truncate: true,
        style: {
          width: '20%',
          minWidth: '200px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: t('tableHeaders.description'),
      cell: info => {
        return <div>{info.getValue() ?? '-'}</div>
      },
      meta: {
        style: {
          width: '35%',
          minWidth: '150px',
        },
      },
    }),
    columnHelper.accessor('contactPerson', {
      header: t('tableHeaders.contactPerson'),
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
    ...(onDeleteClick
      ? [
          columnHelper.display({
            id: 'actions',
            meta: {
              style: {
                width: '5%',
                minWidth: '50px',
                textAlign: 'right',
              },
            },
            header: t('tableHeaders.action'),
            cell: ({ row }) => {
              return (
                <TableDropdownMenu
                  menuItems={[
                    {
                      label: tCommon('actions.delete'),
                      onClick: () => onDeleteClick?.(row.original.id),
                    },
                  ]}
                />
              )
            },
          }),
        ]
      : []),
  ]

  const table = useReactTable({
    getRowId: row => String(row.id),
    columns: columns,
    data: datapools,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    state: { pagination, sorting },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
  })

  return (
    <DataTable
      testId="datapoolsTable"
      table={table}
      pageIndex={pagination.pageIndex}
      pageSize={pagination.pageSize}
      totalPages={table.getPageCount()}
      isLoading={isLoading}
      isPaginationHidden={datapools.length <= pagination.pageSize}
      tableTitle={t('title')}
      tableSubtitle={t('subtitle')}
      tableAction={tableAction}
    />
  )
}
