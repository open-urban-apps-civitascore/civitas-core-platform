import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { CountBadge } from '@/components/count-badge/CountBadge'
import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { InUseIndicator } from '@/components/in-use-indicator/InUseIndicator'
import { StatusBadge } from '@/components/status/status-badge/StatusBadge'
import { VersionStatusCounts } from '@/components/status/version-status-counts/VersionStatusCounts'
import { DataTable } from '@/components/table/DataTable'
import { ExpanderCell } from '@/components/table/expander-cell/ExpanderCell'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { usePermissions } from '@/hooks/use-permissions'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { DatastructuresListData } from '@/types/datastructures'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface DatastructuresTableProps extends TableProps<DatastructuresListData> {
  datastructures: DatastructuresListData[]
  onDeleteDatastructureClick: (id: string) => void
}

export const DatastructuresTable = (props: DatastructuresTableProps) => {
  const {
    datastructures,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onPaginationChange,
    onSortingChange,
    onDeleteDatastructureClick,
  } = props
  const { hasScopedPermission } = usePermissions()
  const t = useTranslations('datastructures')
  const tVersion = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const columnHelper = createColumnHelper<DatastructuresListData>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: ({ row }: CellContext<DatastructuresListData, unknown>) => {
        const href =
          row.depth === 0 ? `datastructures/${row.original.id}` : `datastructures/${row.parentId}/${row.original.id}`
        return (
          <ExpanderCell row={row} className="font-medium">
            <LinkCell
              href={href}
              suffix={row.depth === 0 ? <CountBadge count={row.original.versions.length} /> : undefined}
            >
              {row.original.name}
            </LinkCell>
          </ExpanderCell>
        )
      },
      meta: {
        truncate: true,
        style: {
          minWidth: '200px',
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
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('source', {
      header: t('tableHeaders.source'),
      cell: info => (info.getValue() ? tVersion(`source.${info.getValue()}`) : '-'),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('versionNumber', {
      header: t('tableHeaders.versionNumber'),
      cell: info => info.getValue() || '-',
      meta: {
        truncate: true,
        style: {
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('inUseByReleased', {
      header: tCommon('inUse.columnHeader'),
      cell: ({ row }: CellContext<DatastructuresListData, unknown>) => {
        if (row.depth !== 0) return <InUseIndicator isInUseByReleased={!!row.original.inUseByReleased} />
        const { inUseByReleasedVersionCount, versions } = row.original
        if (!inUseByReleasedVersionCount) return null
        return (
          <div className="flex items-center gap-2 text-muted-foreground">
            <InUseIndicator isInUseByReleased />
            {`${inUseByReleasedVersionCount}/${versions.length}`}
          </div>
        )
      },
      enableSorting: false,
      meta: {
        style: {
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('status', {
      header: t('tableHeaders.status'),
      cell: ({ row }: CellContext<DatastructuresListData, unknown>) => {
        if (row.depth !== 0) {
          const { status } = row.original
          return status ? <StatusBadge status={status} /> : '-'
        }
        return <VersionStatusCounts versionCountByStatus={row.original.versionCountByStatus} />
      },
      meta: {
        style: {
          minWidth: '120px',
        },
      },
    }),
    {
      id: 'actions',
      cell: ({ row }: { row: Row<DatastructuresListData> }) => {
        if (row.depth !== 0) return null
        const canDelete = hasScopedPermission(
          PERMISSION_NAMES.DATASTRUCTURE_DELETE,
          ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
          row.original.id,
        )
        return canDelete ? (
          <TableDropdownMenu
            classNameDropdownContent="w-50"
            menuItems={[
              {
                label: tCommon('actions.removeItem', { item: tCommon('items.datastructure') }),
                onClick: () => onDeleteDatastructureClick(row.original.id),
              },
            ]}
          />
        ) : null
      },
      meta: {
        style: {
          width: '50px',
        },
      },
    },
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: datastructures,
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
    getSubRows: row => row.versions || [],
    getExpandedRowModel: getExpandedRowModel(),
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
