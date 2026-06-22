'use client'

import { DialogProps } from '@radix-ui/react-dialog'
import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  PaginationState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { useGetDatapools } from '@/app/services/api/datapools/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useAddItemSelection } from '@/hooks/use-add-item-selection'
import { useDebounce } from '@/hooks/use-debounce'
import { Datapool } from '@/types/datapools'

interface AddDatapoolModalProps extends DialogProps {
  assignedDatapoolIds: string[]
  onAddDatapools: (datapools: Datapool[]) => void
}

export const AddDatapoolModal = (props: AddDatapoolModalProps) => {
  const { assignedDatapoolIds, open, onOpenChange = () => {}, onAddDatapools } = props

  const t = useTranslations('datasources.datapoolsTab.addDatapoolModal')
  const tCommon = useTranslations('common')

  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: 10 })
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchInput, setSearchInput] = useState('')
  const debouncedSearch = useDebounce(searchInput, 300)

  const queryParams = useMemo(() => {
    const params = new URLSearchParams({
      page: String(pagination.pageIndex),
      size: String(pagination.pageSize),
    })
    if (sorting.length > 0) {
      params.set('sort', `${sorting[0].id},${sorting[0].desc ? 'desc' : 'asc'}`)
    }
    if (debouncedSearch) {
      params.set('q', debouncedSearch)
    }
    return params
  }, [pagination.pageIndex, pagination.pageSize, sorting, debouncedSearch])

  const { data: datapoolsResponse, isLoading, isError } = useGetDatapools({ params: queryParams, isEnabled: !!open })

  const datapools = datapoolsResponse?.data ?? []
  const totalPages = datapoolsResponse?.totalPages ?? 0

  const { selection, selectedItemsRef, assignedIdsSet, handleSelectionChange, newlySelectedCount } =
    useAddItemSelection({ assignedIds: assignedDatapoolIds, items: datapools, open: !!open })

  useEffect(() => {
    if (!open) {
      setPagination({ pageIndex: 0, pageSize: 10 })
      setSorting([])
      setSearchInput('')
    }
  }, [open])

  useEffect(() => {
    setPagination(prev => ({ ...prev, pageIndex: 0 }))
  }, [debouncedSearch])

  const columnHelper = createColumnHelper<Datapool>()

  const columns = useMemo(
    () => [
      columnHelper.accessor('id', {
        header: ({ table }) => (
          <Checkbox
            checked={
              table.getIsAllPageRowsSelected() ? true : table.getIsSomePageRowsSelected() ? 'indeterminate' : false
            }
            onCheckedChange={value => table.toggleAllPageRowsSelected(!!value)}
            aria-label={tCommon('actions.selectAll')}
          />
        ),
        cell: ({ row }) => {
          const isAssigned = assignedIdsSet.has(row.original.id)
          return (
            <Checkbox
              checked={isAssigned || row.getIsSelected()}
              disabled={isAssigned}
              onCheckedChange={value => row.toggleSelected(!!value)}
              aria-label={`${tCommon('actions.select')} ${row.original.name}`}
            />
          )
        },
        meta: { style: { width: '50px' } },
      }),
      columnHelper.accessor('name', {
        header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.name')} />,
        cell: (info: CellContext<Datapool, unknown>) => <div className="font-medium">{info.getValue() as string}</div>,
        meta: { style: { width: '40%' } },
      }),
      columnHelper.accessor('description', {
        header: t('tableHeaders.description'),
        cell: info => info.getValue() ?? '-',
        meta: { style: { width: '60%' } },
      }),
    ],
    [columnHelper, t, tCommon, assignedIdsSet],
  )

  const table = useReactTable({
    getRowId: row => row.id,
    columns,
    data: datapools,
    pageCount: totalPages,
    state: { pagination, sorting, rowSelection: selection },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    enableRowSelection: row => !assignedIdsSet.has(row.original.id),
    onRowSelectionChange: handleSelectionChange,
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
  })

  const onConfirmClick = () => {
    onAddDatapools(Array.from(selectedItemsRef.current.values()))
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="block sm:max-w-[95%] sm:w-[95%] md:max-w-225 h-[80%] max-h-175 [--title-height:64px] [--button-height:60px] [--search-height:64px]">
        <DialogHeader className="mb-4">
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>

        <div className="relative h-[calc(100%-var(--title-height)-var(--button-height))]">
          {isError ? (
            <div className="flex items-center justify-center h-full text-center text-muted-foreground">
              {tCommon('errors.loadingError')}
            </div>
          ) : (
            <>
              <div className="h-(--search-height) mb-4">
                <SearchHeader searchString={searchInput} onChangeSearchString={setSearchInput} />
              </div>
              <div className="h-[calc(100%-var(--search-height)-4rem)]">
                <DataTable
                  table={table}
                  pageIndex={pagination.pageIndex}
                  pageSize={pagination.pageSize}
                  totalPages={totalPages}
                  isLoading={isLoading}
                />
              </div>
              <div className="absolute bottom-6 left-4 text-sm text-muted-foreground">
                {t('selectedDatapools', { number: newlySelectedCount })}
              </div>
            </>
          )}
        </div>

        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={onConfirmClick}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={newlySelectedCount === 0}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
          cancelButtonTitle={tCommon('actions.cancel')}
        />
      </DialogContent>
    </Dialog>
  )
}
