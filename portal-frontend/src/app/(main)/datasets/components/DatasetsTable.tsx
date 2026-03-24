import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { CheckIcon, CircleDashed, UserCheck } from 'lucide-react'
import { useLocale, useTranslations } from 'next-intl'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { DatasetTableData } from '@/types/datasets'
import { TableProps } from '@/types/table'
import { formatDate } from '@/utils/formatDate'
import { resolveUpdater } from '@/utils/table'

interface DatasetsTableProps extends TableProps<DatasetTableData> {
  datasets: DatasetTableData[]
  onDeleteClick?: (datasetId: string | null) => void
}

export const DatasetsTable = (props: DatasetsTableProps) => {
  const {
    datasets,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    onPaginationChange,
    onSortingChange,
    onRowClick,
    isLoading,
    onDeleteClick,
  } = props
  const { hasScopedPermission } = usePermissions()
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')
  const locale = useLocale()

  const columnHelper = createColumnHelper<DatasetTableData>()

  const statusCell = ({ value }: { value: string }) => {
    const statusIconMap = {
      DRAFT: <CircleDashed className="w-4 h-4 text-muted-foreground" />,
      AVAILABLE: <UserCheck className="w-4 h-4 text-muted-foreground" />,
      READY: <CheckIcon className="w-4 h-4 text-muted-foreground" />,
    } as const

    return (
      <div className="flex items-center gap-2">
        {statusIconMap[value as keyof typeof statusIconMap]} {t(`tableValues.${value.toLowerCase()}`)}
      </div>
    )
  }

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
      cell: info => (info.getValue() ? <LinkCell href={`datasets/${info.row.id}`}>{info.getValue()}</LinkCell> : '-'),
      meta: {
        truncate: true,
        style: {
          width: '25%',
        },
      },
    }),
    columnHelper.accessor('createdBy', {
      header: t('tableHeaders.createdBy'),
      cell: info => {
        const user = info.getValue()
        const userNameParts = user?.name.split(' ') || []
        const firstName = userNameParts[0] || ''
        const lastName = userNameParts[userNameParts.length - 1] || ''

        return (
          <LinkCell href={`users/${info.getValue()?.id}`}>
            <div className="flex items-center gap-2">
              {firstName && lastName ? (
                <Avatar className="size-8 rounded-lg">
                  <AvatarImage src="" alt={user?.name || ''} />
                  <AvatarFallback className="rounded-lg">
                    {`${firstName.charAt(0)}${lastName.charAt(0)}` || ''}
                  </AvatarFallback>
                </Avatar>
              ) : null}

              {info.getValue()?.name}
            </div>
          </LinkCell>
        )
      },
      meta: {
        style: {
          width: '22%',
        },
      },
    }),
    columnHelper.accessor('modifiedAt', {
      header: t('tableHeaders.lastUpdated'),
      cell: info => {
        const now = new Date()
        const yesterday = new Date(now)
        yesterday.setDate(yesterday.getDate() - 1)
        yesterday.setHours(0, 0, 0, 0)

        const modifiedDate = new Date(info.getValue())
        modifiedDate.setHours(0, 0, 0, 0)

        const isModifiedAtYesterday = modifiedDate.getTime() === yesterday.getTime()
        return isModifiedAtYesterday ? t('tableValues.yesterday') : formatDate(info.getValue(), locale)
      },
      meta: {
        style: {
          width: '20%',
        },
      },
    }),
    columnHelper.accessor('dataSetStatus', {
      header: t('tableHeaders.status'),
      cell: info => (info.getValue() ? statusCell({ value: info.getValue() }) : '-'),
      meta: {
        style: {
          width: '20%',
        },
      },
    }),
    ...(onDeleteClick
      ? [
          {
            id: 'actions',
            header: t('tableHeaders.action'),
            cell: (info: { row: { id: string | null; original: DatasetTableData } }) =>
              info.row.id && hasScopedPermission(PERMISSION_NAMES.DATASET_DELETE, 'DATASET', info.row.id) ? (
                <TableDropdownMenu
                  menuItems={[{ label: tCommon('actions.delete'), onClick: () => onDeleteClick(info.row.id) }]}
                />
              ) : null,
            meta: { style: { width: '8%' } },
          },
        ]
      : []),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: datasets,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    manualPagination: true,
    manualSorting: true,
    state: { pagination: { pageIndex, pageSize }, sorting },
    onPaginationChange: updater => {
      onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  return (
    <DataTable
      testId="datasetsTable"
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      onRowClick={onRowClick}
      isLoading={isLoading}
    />
  )
}
