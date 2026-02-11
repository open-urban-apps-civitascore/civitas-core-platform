import { createColumnHelper, getCoreRowModel, getSortedRowModel, Row, useReactTable } from '@tanstack/react-table'
import { formatDistanceStrict } from 'date-fns'
import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useLocale, useTranslations } from 'next-intl'

import { ActivityBadge } from '@/components/activity-badge/ActivityBadge'
import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { BadgesWithTooltip } from '@/components/table/badges-with-tooltip/BadgesWithTooltip'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { AppLocale, DATE_LOCALES } from '@/i18n/locales'
import { CONNECTION_TYPES, Datasource, DATASOURCE_STATUS_TYPES } from '@/types/datasources'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface DatasourcesTableProps extends TableProps<Datasource> {
  datasources: Datasource[]
  onDelete?: (id: number) => void
}

export const DatasourcesTable = (props: DatasourcesTableProps) => {
  const {
    datasources,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onRowClick,
    onPaginationChange,
    onSortingChange,
    onDelete,
  } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const locale = useLocale() as AppLocale
  const columnHelper = createColumnHelper<Datasource>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: info => info.getValue(),
      meta: {
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
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '25%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('connector', {
      header: t('tableHeaders.connector'),
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('connection', {
      header: t('tableHeaders.connection'),
      cell: info => {
        const connection = info.getValue()
        let isActive: boolean | undefined
        switch (connection) {
          case CONNECTION_TYPES.INACTIVE:
            isActive = false
            break
          case CONNECTION_TYPES.ACTIVE:
            isActive = true
            break
          default:
            break
        }
        return connection ? <ActivityBadge isActive={isActive} title={connection} /> : '-'
      },
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('lastActive', {
      header: t('tableHeaders.lastActive'),
      cell: info => {
        const interval = formatDistanceStrict(new Date(), new Date(info.getValue()), { locale: DATE_LOCALES[locale] })
        return interval
      },
      meta: {
        style: {
          width: '10%',
          minWidth: '100px',
        },
      },
    }),
    columnHelper.accessor('tags', {
      header: t('tableHeaders.tags'),
      cell: info => <BadgesWithTooltip items={info.getValue()} minVisibleBadges={2} />,
      meta: {
        style: {
          width: '15%',
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
            {status === DATASOURCE_STATUS_TYPES.DRAFT ? (
              <CircleDashed className="text-muted-foreground" size={16} />
            ) : (
              <CircleCheckBig className="text-muted-foreground" size={16} />
            )}
            {t(`status.${info.getValue()}`)}
          </div>
        )
      },
      meta: {
        style: {
          width: '10%',
          minWidth: '120px',
        },
      },
    }),
    {
      id: 'actions',
      cell: ({ row }: { row: Row<Datasource> }) => (
        <TableDropdownMenu
          menuItems={[{ label: tCommon('actions.delete'), onClick: () => onDelete?.(row.original.id) }]}
        />
      ),
    },
  ]

  const table = useReactTable({
    getRowId: row => String(row.id),
    columns: columns,
    data: datasources,
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
      testId="datasourcesTable"
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      onRowClick={onRowClick}
    />
  )
}
