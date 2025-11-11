import { CellContext, createColumnHelper, getCoreRowModel, Row, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Button } from '@/components/ui/button'
import { Permission } from '@/types/permissions'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface PermissionsTableProps extends TableProps<Permission> {
  permissions: Permission[]
  onOpenButtonClick: (row: Row<Permission>) => void
}

export const PermissionsTable = (props: PermissionsTableProps) => {
  const {
    permissions,
    isLoading,
    sorting,
    pageIndex,
    pageSize,
    totalPages,
    rowSelection,
    rowCount,
    onOpenButtonClick,
    onPaginationChange,
    onSortingChange,
  } = props

  const t = useTranslations('permissions')
  const tCommon = useTranslations('common')

  const columnHelper = createColumnHelper<Permission>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'ID',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('title', {
      header: ({ column }) => {
        return <SortableTableHeader column={column} title={t('tableHeaders.title')} />
      },
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '40%',
        },
      },
    }),
    columnHelper.accessor('category', {
      header: () => t('tableHeaders.category'),
      cell: info => info.getValue().title,
    }),
    {
      id: 'action',
      cell: ({ row }: CellContext<Permission, string>) => (
        <Button
          className="opacity-0 group-hover:opacity-100 transition-opacity"
          variant="outline"
          onClick={() => onOpenButtonClick(row)}
        >
          {tCommon('actions.open')}
        </Button>
      ),
      meta: {
        style: {
          width: '108px',
        },
      },
    },
  ]

  const table = useReactTable({
    columns: columns,
    data: permissions,
    getCoreRowModel: getCoreRowModel(),
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    rowCount,
    manualPagination: true,
    manualSorting: true,
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection },
    onPaginationChange: updater => {
      onPaginationChange(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => onSortingChange(resolveUpdater(updater, sorting)),
  })

  return (
    <DataTable table={table} pageIndex={pageIndex} pageSize={pageSize} totalPages={totalPages} isLoading={isLoading} />
  )
}
