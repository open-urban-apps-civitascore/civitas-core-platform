import { DialogProps } from '@radix-ui/react-dialog'
import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  PaginationState,
  Row,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useRef, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { ExpanderCell } from '@/components/table/expander-cell/ExpanderCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'
import { resolveUpdater } from '@/utils/table'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

interface GroupAssignmentModalProps extends DialogProps {
  selection: RowSelectionState
  setSelection: (selection: RowSelectionState) => void
  originalSelection: RowSelectionState
  onGroupAssignmentUpdate: (groupIds: string[]) => void
  haveGroupsBeenTouched: boolean
  roleName: Role['name']
}

export const GroupAssignmentModal = (props: GroupAssignmentModalProps) => {
  const {
    open,
    onOpenChange = () => {},
    selection,
    setSelection,
    originalSelection,
    onGroupAssignmentUpdate,
    haveGroupsBeenTouched,
    roleName,
  } = props
  const tRoles = useTranslations('roles')
  const tCommon = useTranslations('common')
  const [groups, setGroups] = useState<Group[]>([])
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [isLoading, setIsLoading] = useState(false)
  const [sorting, setSorting] = useState<SortingState>([])
  const [rowCount, setRowCount] = useState(0)
  const [searchString, setSearchString] = useState('')
  const [totalPages, setTotalPages] = useState(Math.ceil(rowCount / pageSize))
  const selectAllCheckbox = useRef<HTMLButtonElement>(null)
  const { getApiRequestParams } = useQueryParams()

  const getGroupsData = async () => {
    const requestParams = getApiRequestParams({ pageIndex, pageSize, sorting, search: searchString })
    setIsLoading(true)

    try {
      const groupsResponse = await fetch(`${URL}/groups?${requestParams.toString()}`, {
        cache: 'no-store',
      })

      if (!groupsResponse) {
        throw new Error('An error occurred while loading group data')
      }

      const groupData: Group[] = await groupsResponse.json()
      setGroups(groupData)

      const totalCount = Number(groupsResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }
      setTotalPages(Math.ceil(totalCount / pageSize))
    } catch (error) {
      console.error(error)

      throw new Error('An error occurred while loading group data')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    getGroupsData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, rowCount, sorting, searchString])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  // for accessibility: avoid losing focus after toggeling checkboxes via keyboard
  const setFocus = (elementId: string) => {
    requestAnimationFrame(() => {
      const element = document.getElementById(elementId)
      element?.focus()
    })
  }

  const handleCheckedChange = (value: string | boolean, row: Row<Group>) => {
    setFocus(row.id)
    row.toggleSelected(!!value)
    const isRowSelected = row.getIsSelected()
    row.toggleExpanded(!isRowSelected)
    row.subRows?.forEach(subRow => subRow.toggleExpanded(!isRowSelected))
  }

  const columnHelper = createColumnHelper<Group>()

  const columns = [
    columnHelper.accessor('id', {
      header: () => (
        <Checkbox
          ref={selectAllCheckbox}
          checked={
            table.getIsAllPageRowsSelected() ? true : table.getIsSomePageRowsSelected() ? 'indeterminate' : false
          }
          onCheckedChange={value => table.toggleAllPageRowsSelected(!!value)}
          id="selectAll"
        />
      ),
      cell: ({ row }) => (
        <Checkbox
          checked={
            row.getIsSelected()
              ? true
              : row.getIsSomeSelected() || row.getIsAllSubRowsSelected()
                ? 'indeterminate'
                : false
          }
          onCheckedChange={value => handleCheckedChange(value, row)}
          aria-label={`Select group ${row.original.title}`}
          id={row.id}
        />
      ),
    }),
    columnHelper.accessor('title', {
      header: ({ column }) => (
        <SortableTableHeader column={column} title={tRoles('groupAssignmentTab.modal.tableHeaders.name')} />
      ),
      cell: ({ row }: CellContext<Group, unknown>) => (
        <ExpanderCell row={row} value={row.original.title} className="font-medium" />
      ),
      meta: {
        style: {
          width: '35%',
          color: 'var(--foreground)',
        },
      },
    }),
    columnHelper.accessor('users', {
      header: tRoles('groupAssignmentTab.modal.tableHeaders.usersCount'),
      cell: info => info.getValue()?.length,
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
    manualPagination: true,
    manualSorting: true,
    getSubRows: row => row.subgroups || [],
    getExpandedRowModel: getExpandedRowModel(),
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onRowSelectionChange: updater => {
      setSelection(resolveUpdater(updater, selection))
    },
    onPaginationChange: updater => {
      handlePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onSortingChange: updater => setSorting(resolveUpdater(updater, sorting)),
  })

  const groupIdsToAssign = Object.keys(selection).map(key => key)

  const onConfirmClick = () => {
    onGroupAssignmentUpdate(groupIdsToAssign)
    onOpenChange(false)
  }

  const onCancelClick = () => {
    setSelection(originalSelection)
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
            isLoading={isLoading}
          />
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={onConfirmClick}
          onCancelClick={() => onCancelClick()}
          isConfirmButtonDisabled={!haveGroupsBeenTouched}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
        />
      </DialogContent>
    </Dialog>
  )
}
