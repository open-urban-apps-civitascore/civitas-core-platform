'use client'

import {
  createColumnHelper,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  type PaginationState,
  type RowSelectionState,
  type SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { StatusBadge } from '@/components/status-badge/StatusBadge'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { type Datasource } from '@/types/datasources'

interface DataSourceSelectModalProps {
  isOpen: boolean
  onOpenChange: (open: boolean) => void
  datasources: Datasource[]
  isLoading: boolean
  selectedId: string | undefined
  onSelect: (datasource: Datasource | undefined) => void
}

const matchesSearch = (datasource: Datasource, search: string) => {
  const needle = search.trim().toLowerCase()
  if (!needle) return true
  return [datasource.name, datasource.description, datasource.connector?.connectorType].some(value =>
    value?.toLowerCase().includes(needle),
  )
}

export const DataSourceSelectModal = ({
  isOpen,
  onOpenChange,
  datasources,
  isLoading,
  selectedId,
  onSelect,
}: DataSourceSelectModalProps) => {
  const t = useTranslations('pipelineEditor.dataSourcePanel.selectDialog')
  const tHeaders = useTranslations('datasources.tableHeaders')
  const [searchString, setSearchString] = useState('')
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: 10 })
  const [selection, setSelection] = useState<RowSelectionState>({})

  useEffect(() => {
    setSelection(isOpen && selectedId ? { [selectedId]: true } : {})
  }, [isOpen, selectedId])

  const filteredDatasources = useMemo(
    () => datasources.filter(datasource => matchesSearch(datasource, searchString)),
    [datasources, searchString],
  )

  const columnHelper = createColumnHelper<Datasource>()
  const columns = [
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={tHeaders('name')} />,
      cell: ({ row }) => (
        <div className="flex items-center gap-3 font-medium">
          <Checkbox
            checked={row.getIsSelected()}
            onCheckedChange={value => row.toggleSelected(!!value)}
            aria-label={row.original.name}
          />
          <span>{row.original.name}</span>
        </div>
      ),
      meta: { style: { minWidth: '200px', color: 'var(--foreground)', fontWeight: '500' } },
    }),
    columnHelper.accessor('description', {
      header: tHeaders('description'),
      cell: info => info.getValue() ?? '-',
      meta: { style: { minWidth: '200px' } },
    }),
    columnHelper.accessor(row => row.connector?.connectorType, {
      id: 'connectorType',
      header: tHeaders('connector'),
      cell: info => info.getValue() ?? '-',
      meta: { style: { minWidth: '100px' } },
    }),
    columnHelper.accessor('dataSourceStatus', {
      header: tHeaders('status'),
      cell: info => <StatusBadge status={info.getValue()} />,
      meta: { style: { minWidth: '120px' } },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns,
    data: filteredDatasources,
    state: { pagination, sorting, rowSelection: selection },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
    onRowSelectionChange: updater => {
      const next = typeof updater === 'function' ? updater(selection) : updater
      const newlySelectedId = Object.keys(next).find(id => next[id] && !selection[id])
      setSelection(newlySelectedId ? { [newlySelectedId]: true } : {})
    },
  })

  const selectedDatasourceId = Object.keys(selection)[0]
  const isConfirmDisabled = isLoading || selectedDatasourceId === selectedId

  const handleSearch = (search: string) => {
    setSearchString(search)
    setPagination(previous => ({ ...previous, pageIndex: 0 }))
  }

  const handleConfirm = () => {
    onSelect(datasources.find(datasource => datasource.id === selectedDatasourceId))
  }

  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
      <DialogContent
        data-testid="dataSourceSelectModal"
        className="block sm:max-w-[95%] sm:w-[95%] md:max-w-[1061px] h-[80%] max-h-[743px] [--title-height:64px] [--button-height:60px]"
      >
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>
        <SearchHeader searchString={searchString} onChangeSearchString={handleSearch} className="my-2" />
        <div className="h-[calc(100%-var(--search-height)-var(--title-height)-var(--button-height))]">
          <DataTable
            table={table}
            pageIndex={pagination.pageIndex}
            pageSize={pagination.pageSize}
            totalPages={table.getPageCount()}
            isLoading={isLoading}
          />
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={handleConfirm}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={isConfirmDisabled}
          hasCard={false}
          confirmButtonTitle={t('confirm')}
        />
      </DialogContent>
    </Dialog>
  )
}
