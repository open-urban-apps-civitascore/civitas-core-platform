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
import { useEffect, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { ExpanderCell } from '@/components/table/expander-cell/ExpanderCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useAddItemSelection } from '@/hooks/use-add-item-selection'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'
import { isPageIndexHigherThanTotalPages, resolveUpdater } from '@/utils/table'

interface GroupAssignmentModalProps extends DialogProps {
  assignedGroupIds: string[]
  onAddGroups: (groupIds: string[]) => void
  roleName: Role['name']
}

export const GroupAssignmentModal = (props: GroupAssignmentModalProps) => {
  const { open, onOpenChange = () => {}, assignedGroupIds, onAddGroups, roleName } = props
  const tRoles = useTranslations('roles')
  const tCommon = useTranslations('common')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const [totalPages, setTotalPages] = useState(0)
  const { getApiRequestParams } = useQueryParams()

  const requestParams = getApiRequestParams({ pageIndex, pageSize, sorting, search: searchString })
  const { data: groupsData, isFetching } = useGetGroups({ params: requestParams })

  const groups = groupsData?.data || []
  const rowCount = groupsData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, pageSize])

  useEffect(() => {
    if (isPageIndexHigherThanTotalPages(pageIndex, totalPages)) {
      setPageIndex(totalPages - 1)
    }
  }, [pageIndex, totalPages])

  const { selection, selectedItemsRef, assignedIdsSet, handleSelectionChange, newlySelectedCount } =
    useAddItemSelection({ assignedIds: assignedGroupIds, items: groups, open: !!open })

  useEffect(() => {
    if (!open) {
      setSearchString('')
      setPageIndex(0)
      setSorting([])
    }
  }, [open])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const setFocus = (elementId: string) => {
    requestAnimationFrame(() => {
      const element = document.getElementById(elementId)
      element?.focus()
    })
  }

  const columnHelper = createColumnHelper<Group>()

  const columns = [
    columnHelper.accessor('id', {
      header: () => (
        <Checkbox
          checked={
            table.getIsAllPageRowsSelected() ? true : table.getIsSomePageRowsSelected() ? 'indeterminate' : false
          }
          onCheckedChange={value => table.toggleAllPageRowsSelected(!!value)}
          id="selectAll"
        />
      ),
      cell: ({ row }) => {
        const isAssigned = assignedIdsSet.has(row.original.id)
        return (
          <Checkbox
            checked={isAssigned || row.getIsSelected()}
            onCheckedChange={value => {
              setFocus(row.id)
              row.toggleSelected(!!value)
            }}
            disabled={isAssigned}
            aria-label={`Select group ${row.original.name}`}
            id={row.id}
          />
        )
      },
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => (
        <SortableTableHeader column={column} title={tRoles('groupAssignmentTab.modal.tableHeaders.name')} />
      ),
      cell: ({ row }: CellContext<Group, unknown>) => (
        <ExpanderCell row={row} className="font-medium">
          {row.original.name}
        </ExpanderCell>
      ),
      meta: {
        style: {
          width: '35%',
          color: 'var(--foreground)',
        },
      },
    }),
    columnHelper.accessor('members', {
      header: tRoles('groupAssignmentTab.modal.tableHeaders.usersCount'),
      cell: info => info.getValue()?.length || 0,
      meta: {
        style: { width: '10%' },
      },
    }),
    columnHelper.accessor('description', {
      header: tRoles('groupAssignmentTab.modal.tableHeaders.description'),
      cell: info => info.getValue(),
      meta: {
        style: {
          maxWidth: '45%',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: groups,
    rowCount,
    initialState: {
      columnVisibility: {
        parent: false,
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting, rowSelection: selection },
    enableRowSelection: row => !assignedIdsSet.has(row.original.id),
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    onRowSelectionChange: handleSelectionChange,
    onPaginationChange: updater => {
      handlePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => setSorting(resolveUpdater(updater, sorting)),
  })

  const onConfirmClick = () => {
    onAddGroups(Array.from(selectedItemsRef.current.keys()))
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="block sm:max-w-[95%] sm:w-[95%] md:max-w-[1061px] h-[80%] max-h-[743px]  [--title-height:64px] [--button-height:60px]">
        <DialogHeader>
          <DialogTitle>{tRoles('groupAssignmentTab.modal.title')}</DialogTitle>
          <DialogDescription>
            {tRoles('groupAssignmentTab.modal.description', { roleName: roleName })}
          </DialogDescription>
        </DialogHeader>
        <SearchHeader
          searchString={searchString}
          onChangeSearchString={newSearchString => setSearchString(newSearchString)}
          className="my-2"
        />
        <div className="h-[calc(100%-var(--search-height)-var(--title-height)-var(--button-height))]">
          <DataTable
            table={table}
            pageIndex={pageIndex}
            pageSize={pageSize}
            totalPages={totalPages}
            isLoading={isFetching}
          />
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={onConfirmClick}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={newlySelectedCount === 0}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
        />
      </DialogContent>
    </Dialog>
  )
}
