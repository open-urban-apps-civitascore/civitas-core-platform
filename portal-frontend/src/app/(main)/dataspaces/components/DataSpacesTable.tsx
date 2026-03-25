import { createColumnHelper, getCoreRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'

import { StatusLabel } from '@/components/status-label/StatusLabel'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { DataSpace } from '@/types/dataspaces'
import { TableProps } from '@/types/table'
import { resolveUpdater } from '@/utils/table'

interface DataSpacesTableProps extends TableProps<DataSpace> {
  dataspaces: DataSpace[]
}

export const DataSpacesTable = (props: DataSpacesTableProps) => {
  const {
    dataspaces,
    rowCount,
    pageIndex,
    totalPages,
    pageSize,
    sorting,
    rowSelection,
    onRowClick,
    onPaginationChange,
    onSortingChange,
    isLoading,
  } = props
  const t = useTranslations('dataspaces')
  const columnHelper = createColumnHelper<DataSpace>()

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
        truncate: true,
        style: {
          width: '25%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.description')} />,
      cell: info => info.getValue(),
      meta: {
        truncate: true,
        style: {
          width: '50%',
        },
      },
    }),
    columnHelper.accessor('protected', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.protected')} />,
      cell: info => <StatusLabel isChecked={info.getValue() as boolean} />,
      meta: {
        style: {
          width: '25%',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: dataspaces,
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
      table={table}
      pageIndex={pageIndex}
      pageSize={pageSize}
      totalPages={totalPages}
      onRowClick={onRowClick}
      isLoading={isLoading}
    />
  )
}
