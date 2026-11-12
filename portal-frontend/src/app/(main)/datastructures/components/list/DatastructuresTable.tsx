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
  isDuplicating: boolean
  onDeleteDatastructureClick: (id: string) => void
  onDeleteVersionClick: (datastructureId: string, versionId: string) => void
  onDuplicateVersionClick: (datastructureId: string, versionId: string) => void
}

export const DatastructuresTable = (props: DatastructuresTableProps) => {
  const {
    datastructures,
    isDuplicating,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onPaginationChange,
    onSortingChange,
    onDeleteDatastructureClick,
    onDeleteVersionClick,
    onDuplicateVersionClick,
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
        const isVersionRow = row.depth > 0
        const datastructureId = isVersionRow ? (row.parentId as string) : row.original.id
        const canDelete = hasScopedPermission(
          PERMISSION_NAMES.DATASTRUCTURE_DELETE,
          ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
          datastructureId,
        )
        const canDuplicate =
          isVersionRow &&
          hasScopedPermission(
            PERMISSION_NAMES.DATASTRUCTURE_UPDATE,
            ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
            datastructureId,
          )
        const duplicateItems = canDuplicate
          ? [
              {
                label: tCommon('actions.duplicateItem', { item: tCommon('items.datastructureVersion') }),
                onClick: () => onDuplicateVersionClick(datastructureId, row.original.id),
                disabled: isDuplicating,
              },
            ]
          : []
        const deleteItems = canDelete
          ? [
              {
                label: tCommon('actions.deleteItem', {
                  item: tCommon(isVersionRow ? 'items.datastructureVersion' : 'items.datastructure'),
                }),
                onClick: () =>
                  isVersionRow
                    ? onDeleteVersionClick(datastructureId, row.original.id)
                    : onDeleteDatastructureClick(row.original.id),
              },
            ]
          : []
        const menuItems = [...duplicateItems, ...deleteItems]
        return menuItems.length > 0 ? <TableDropdownMenu classNameDropdownContent="w-50" menuItems={menuItems} /> : null
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
