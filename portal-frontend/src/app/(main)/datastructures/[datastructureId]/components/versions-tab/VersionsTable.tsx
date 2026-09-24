import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { InUseIndicator } from '@/components/in-use-indicator/InUseIndicator'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { DATASTRUCTURE_STATUS_TYPES, DatastructureVersionsListData } from '@/types/datastructures'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface VersionsTableProps extends TableProps<DatastructureVersionsListData> {
  datastructureId: string
  versions: DatastructureVersionsListData[]
  onDelete?: (id: string) => void
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
      cell: info => {
        const status = info.getValue()
        return (
          <div className="flex items-center gap-2">
            {status === DATASTRUCTURE_STATUS_TYPES.DRAFT ? (
              <CircleDashed className="text-muted-foreground" size={16} />
            ) : (
              <CircleCheckBig className="text-muted-foreground" size={16} />
            )}
            {tCommon(`status.${info.getValue()}`)}
          </div>
        )
      },
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
