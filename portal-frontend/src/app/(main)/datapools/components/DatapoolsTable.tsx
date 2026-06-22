import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { ContactCell } from '@/components/table/contact-cell/ContactCell'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { usePermissions } from '@/hooks/use-permissions'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { DatapoolSummary, DatapoolTabValues } from '@/types/datapools'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

export interface DatapoolsTableProps extends TableProps<DatapoolSummary> {
  datapools: DatapoolSummary[]
  onDeleteClick?: (datapool: DatapoolSummary) => void
}

export const DatapoolsTable = (props: DatapoolsTableProps) => {
  const {
    datapools,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onRowClick,
    onPaginationChange,
    onSortingChange,
    onDeleteClick,
  } = props

  const t = useTranslations('datapools')
  const tCommon = useTranslations('common')
  const { hasScopedPermission } = usePermissions()

  const columnHelper = createColumnHelper<DatapoolSummary>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: info =>
        info.getValue() ? <LinkCell href={`datapools/${info.row.original.id}`}>{info.getValue()}</LinkCell> : '-',

      meta: {
        truncate: true,
        style: {
          width: '15%',
          minWidth: '200px',
          color: 'var(--foreground)',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('datasets', {
      header: t('tableHeaders.datasets'),
      cell: info => {
        return (
          <LinkCell href={`/datapools/${info.row.original.id}?subtab=${DatapoolTabValues.datasets}`}>
            <div className="inline-flex items-center justify-center rounded-full bg-primary/10 px-2 py-0.5 font-medium text-primary">
              {info.getValue().length}
            </div>
          </LinkCell>
        )
      },
      meta: {
        style: {
          width: '15%',
          minWidth: '100px',
          textAlign: 'center',
        },
      },
    }),
    columnHelper.accessor('contactPerson', {
      header: t('tableHeaders.contact'),
      cell: info => {
        if (!info.getValue()) {
          return <span className="text-sm text-foreground">{t('noContact')}</span>
        }
        return <ContactCell user={info.getValue() ?? null} />
      },
      meta: {
        style: {
          width: '15%',
          minWidth: '150px',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: t('tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        truncate: true,
        style: {
          width: '30%',
          minWidth: '200px',
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
              const canDelete = hasScopedPermission(
                PERMISSION_NAMES.DATAPOOL_DELETE,
                ASSIGNMENT_SCOPE_TYPES.DATAPOOL,
                row.original.id,
              )
              return canDelete ? (
                <TableDropdownMenu
                  menuItems={[
                    {
                      label: tCommon('actions.delete'),
                      onClick: () => onDeleteClick?.(row.original),
                    },
                  ]}
                />
              ) : null
            },
          }),
        ]
      : []),
  ]

  const table = useReactTable({
    getRowId: row => String(row.id),
    columns: columns,
    data: datapools,
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
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
      testId="datapoolsTable"
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      onRowClick={onRowClick}
    />
  )
}
