import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { InUseIndicator } from '@/components/in-use-indicator/InUseIndicator'
import { StatusBadge } from '@/components/status-badge/StatusBadge'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { DatastructureVersionsListData } from '@/types/datastructures'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface VersionsTableProps extends TableProps<DatastructureVersionsListData> {
  datastructureId: string
  versions: DatastructureVersionsListData[]
}

export const VersionsTable = (props: VersionsTableProps) => {
  const {
    datastructureId,
    versions,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onPaginationChange,
    onSortingChange,
  } = props
  const t = useTranslations('datastructures')
  const tVersion = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const columnHelper = createColumnHelper<DatastructureVersionsListData>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('versionNumber', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.versionNumber')} />,
      cell: ({ row }) => (
        <LinkCell href={`/datastructures/${datastructureId}/${row.original.id}`}>
          {row.original.versionNumber ?? '-'}
        </LinkCell>
      ),
      meta: {
        truncate: true,
        style: {
          width: '20%',
          minWidth: '100px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: t('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        truncate: true,
        style: {
          width: '25%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('source', {
      header: t('tableHeaders.source'),
      cell: info => (info.getValue() ? tVersion(`source.${info.getValue()}`) : '-'),
      meta: {
        style: {
          width: '5%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('inUseByReleased', {
      header: tCommon('inUse.columnHeader'),
      cell: info => <InUseIndicator isInUseByReleased={!!info.getValue()} />,
      enableSorting: false,
      meta: {
        style: {
          width: '5%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('status', {
      header: t('tableHeaders.status'),
      cell: info => <StatusBadge status={info.getValue()} />,
      meta: {
        style: {
          width: '5%',
          minWidth: '120px',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: versions,
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
        parent: false,
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  return (
    <DataTable
      testId="DatastructuresTable"
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
    />
  )
}
