import { DialogProps } from '@radix-ui/react-dialog'
import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  PaginationState,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Group } from '@/types/groups'
import { resolveUpdater } from '@/utils/table'

import { SearchHeader } from '../search-area/SearchArea'

interface AddGroupModalProps extends DialogProps {
  groups: Group[]
  assignedGroupIds: string[]
  onAddGroups: (groupIds: string[]) => void
}

export const AddGroupModal = (props: AddGroupModalProps) => {
  const { groups, assignedGroupIds, open, onOpenChange = () => {}, onAddGroups } = props

  const t = useTranslations('accessManagement')
  const tCommon = useTranslations('common')

  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 10,
  })

  const [sorting, setSorting] = useState<SortingState>([])
  const [selection, setSelection] = useState<RowSelectionState>({})
  const [searchInput, setSearchInput] = useState('')

  const availableGroups = useMemo(() => {
    return groups.filter(group => !assignedGroupIds.includes(group.id))
  }, [groups, assignedGroupIds])

  const resetSelection = () => {
    setSelection({})
    setPagination({ pageIndex: 0, pageSize: 10 })
    setSorting([])
    setSearchInput('')
  }

  useEffect(() => {
    if (!open) {
      resetSelection()
    }
  }, [open])

  const columnHelper = createColumnHelper<Group>()

  const columns = useMemo(
    () => [
      columnHelper.accessor('id', {
        header: ({ table }) => (
          <Checkbox
            checked={
              table.getIsAllPageRowsSelected() ? true : table.getIsSomePageRowsSelected() ? 'indeterminate' : false
            }
            onCheckedChange={value => table.toggleAllPageRowsSelected(!!value)}
            aria-label={t('addGroupModal.selectAll')}
          />
        ),
        cell: ({ row }) => (
          <Checkbox
            checked={row.getIsSelected()}
            onCheckedChange={value => row.toggleSelected(!!value)}
            aria-label={`${t('addGroupModal.selectGroup')} ${row.original.name}`}
          />
        ),
        meta: {
          style: { width: '50px' },
        },
      }),

      columnHelper.accessor('name', {
        header: ({ column }) => <SortableTableHeader column={column} title={t('addGroupModal.tableHeaders.name')} />,
        cell: (info: CellContext<Group, unknown>) => <div className="font-medium">{info.getValue() as string}</div>,
        meta: {
          style: { width: '40%' },
        },
      }),

      columnHelper.accessor('description', {
        header: t('addGroupModal.tableHeaders.description'),
        cell: info => info.getValue(),
        meta: {
          style: { width: '60%' },
        },
      }),
    ],
    [columnHelper, t],
  )

  const table = useReactTable({
    getRowId: row => row.id,
    columns,
    data: availableGroups,
    state: {
      pagination: pagination,
      sorting,
      rowSelection: selection,
      globalFilter: searchInput,
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onRowSelectionChange: updater => {
      setSelection(resolveUpdater(updater, selection))
    },
    onPaginationChange: setPagination,
    onSortingChange: updater => {
      setSorting(resolveUpdater(updater, sorting))
    },
    getFilteredRowModel: getFilteredRowModel(),
    onGlobalFilterChange: setSearchInput,
  })

  const selectedGroupIds = Object.keys(selection)

  const onConfirmClick = () => {
    if (selectedGroupIds.length > 0) {
      onAddGroups(selectedGroupIds)
    }
    onOpenChange(false)
  }

  const onCancelClick = () => {
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="block sm:max-w-[95%] sm:w-[95%] md:max-w-225 h-[80%] max-h-175 [--title-height:64px] [--button-height:60px] [--search-height:64px]">
        <DialogHeader className="mb-4">
          <DialogTitle>{t('addGroupModal.title')}</DialogTitle>
          <DialogDescription>{t('addGroupModal.description')}</DialogDescription>
        </DialogHeader>

        <div className="relative h-[calc(100%-var(--title-height)-var(--button-height))]">
          {availableGroups.length === 0 ? (
            <div className="flex items-center justify-center h-full text-center text-muted-foreground">
              {t('addGroupModal.noGroupsAvailable')}
            </div>
          ) : (
            <>
              <div className="h-(--search-height) mb-4">
                <SearchHeader searchString={searchInput} onChangeSearchString={value => setSearchInput(value)} />
              </div>
              <div className="h-[calc(100%-var(--search-height)-4rem)]">
                <DataTable
                  table={table}
                  pageIndex={pagination.pageIndex}
                  pageSize={pagination.pageSize}
                  totalPages={table.getPageCount()}
                  isLoading={false}
                />
              </div>

              <div className="absolute bottom-6 left-4 text-sm text-muted-foreground">
                {t('addGroupModal.selectedGroups', {
                  number: selectedGroupIds.length,
                })}
              </div>
            </>
          )}
        </div>

        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={onConfirmClick}
          onCancelClick={onCancelClick}
          isConfirmButtonDisabled={selectedGroupIds.length === 0}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
          cancelButtonTitle={tCommon('actions.cancel')}
        />
      </DialogContent>
    </Dialog>
  )
}
