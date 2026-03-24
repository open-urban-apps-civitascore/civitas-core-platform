import { DialogProps } from '@radix-ui/react-dialog'
import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  PaginationState,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Group } from '@/types/groups'

import { SearchHeader } from '../search-area/SearchArea'

interface AddGroupModalProps extends DialogProps {
  assignedGroupIds: string[]
  onAddGroups: (groups: Group[]) => void
}

export const AddGroupModal = (props: AddGroupModalProps) => {
  const { assignedGroupIds, open, onOpenChange = () => {}, onAddGroups } = props

  const t = useTranslations('accessManagement')
  const tCommon = useTranslations('common')

  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 10,
  })

  const [sorting, setSorting] = useState<SortingState>([])
  const [selection, setSelection] = useState<RowSelectionState>({})
  const [searchInput, setSearchInput] = useState('')

  // Track selected Group objects across pages since server-side pagination
  // only keeps the current page's data in the response
  const selectedGroupsRef = useRef<Map<string, Group>>(new Map())

  const assignedGroupIdsSet = useMemo(() => new Set(assignedGroupIds), [assignedGroupIds])

  const queryParams = useMemo(() => {
    const params = new URLSearchParams({
      page: String(pagination.pageIndex),
      size: String(pagination.pageSize),
    })
    if (sorting.length > 0) {
      params.set('sort', `${sorting[0].id},${sorting[0].desc ? 'desc' : 'asc'}`)
    }
    if (searchInput) {
      params.set('q', searchInput)
    }
    return params
  }, [pagination.pageIndex, pagination.pageSize, sorting, searchInput])

  const { data: groupsResponse, isLoading, isError } = useGetGroups({ params: queryParams })

  const groups = groupsResponse?.data ?? []
  const totalPages = groupsResponse?.totalPages ?? 0
  const hasData = groups.length > 0 || isLoading

  const resetState = () => {
    setSelection({})
    setPagination({ pageIndex: 0, pageSize: 10 })
    setSorting([])
    setSearchInput('')
    selectedGroupsRef.current.clear()
  }

  useEffect(() => {
    if (!open) {
      resetState()
    }
  }, [open])

  // Reset page index when search changes
  useEffect(() => {
    setPagination(prev => ({ ...prev, pageIndex: 0 }))
  }, [searchInput])

  const handleSelectionChange = (updater: RowSelectionState | ((old: RowSelectionState) => RowSelectionState)) => {
    const newSelection = typeof updater === 'function' ? updater(selection) : updater
    // Track newly selected groups, ignore already-assigned ones
    const addedIds = Object.keys(newSelection).filter(id => !selection[id] && !assignedGroupIdsSet.has(id))
    const removedIds = Object.keys(selection).filter(id => !newSelection[id] && !assignedGroupIdsSet.has(id))
    for (const id of addedIds) {
      const group = groups.find(g => g.id === id)
      if (group) selectedGroupsRef.current.set(id, group)
    }
    for (const id of removedIds) {
      selectedGroupsRef.current.delete(id)
    }
    // Keep assigned IDs in selection (they're always checked)
    const filtered: RowSelectionState = {}
    for (const id of Object.keys(newSelection)) {
      if (!assignedGroupIdsSet.has(id)) {
        filtered[id] = true
      }
    }
    setSelection(filtered)
  }

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
        cell: ({ row }) => {
          const isAssigned = assignedGroupIdsSet.has(row.original.id)
          return (
            <Checkbox
              checked={isAssigned || row.getIsSelected()}
              disabled={isAssigned}
              onCheckedChange={value => row.toggleSelected(!!value)}
              aria-label={`${t('addGroupModal.selectGroup')} ${row.original.name}`}
            />
          )
        },
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
    [columnHelper, t, assignedGroupIdsSet],
  )

  const table = useReactTable({
    getRowId: row => row.id,
    columns,
    data: groups,
    pageCount: totalPages,
    state: {
      pagination,
      sorting,
      rowSelection: selection,
    },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    enableRowSelection: row => !assignedGroupIdsSet.has(row.original.id),
    onRowSelectionChange: handleSelectionChange,
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
  })

  const newlySelectedCount = selectedGroupsRef.current.size

  const onConfirmClick = () => {
    if (newlySelectedCount > 0) {
      onAddGroups(Array.from(selectedGroupsRef.current.values()))
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
          {!hasData && !isError ? (
            <div className="flex items-center justify-center h-full text-center text-muted-foreground">
              {t('addGroupModal.noGroupsAvailable')}
            </div>
          ) : isError ? (
            <div className="flex items-center justify-center h-full text-center text-muted-foreground">
              {tCommon('errors.loadingError')}
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
                  totalPages={totalPages}
                  isLoading={isLoading}
                />
              </div>

              <div className="absolute bottom-6 left-4 text-sm text-muted-foreground">
                {t('addGroupModal.selectedGroups', {
                  number: newlySelectedCount,
                })}
              </div>
            </>
          )}
        </div>

        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={onConfirmClick}
          onCancelClick={onCancelClick}
          isConfirmButtonDisabled={newlySelectedCount === 0}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
          cancelButtonTitle={tCommon('actions.cancel')}
        />
      </DialogContent>
    </Dialog>
  )
}
