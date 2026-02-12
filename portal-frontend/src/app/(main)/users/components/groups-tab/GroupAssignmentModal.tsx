import { DialogProps } from '@radix-ui/react-dialog'
import {
  createColumnHelper,
  getCoreRowModel,
  getSortedRowModel,
  PaginationState,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useQueryParams } from '@/hooks/use-query-params'
import { UserGroupsListData } from '@/types/groups'
import { setFocus } from '@/utils/common'
import { mapGroupsApiToListData } from '@/utils/groups'
import { isPageIndexHigherThanTotalPages, resolveUpdater } from '@/utils/table'

export type UserSelection = { selectAll: boolean; selectedIds: string[]; excludedIds: string[] }

interface GroupAssignmentModalProps extends DialogProps {
  originalGroups: string[]
  userName: string
  onAssignGroups: (groupSelection: RowSelectionState) => void
  isUpdating?: boolean
}

export const GroupAssignmentModal = (props: GroupAssignmentModalProps) => {
  const { originalGroups, userName, open, onOpenChange = () => {}, onAssignGroups, isUpdating = false } = props
  const tCommon = useTranslations('common')
  const t = useTranslations('users')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const [selection, setSelection] = useState<RowSelectionState>({})
  const selectAllCheckbox = useRef<HTMLButtonElement>(null)
  const { getApiRequestParams } = useQueryParams()

  const { data: groupsData, isFetching: isFetchingGroups } = useGetGroups({
    params: getApiRequestParams({ pageIndex, pageSize, sorting, search: searchString }),
  })

  const groups = useMemo(() => {
    if (groupsData?.data && groupsData?.data.length > 0) {
      const allUsers = mapGroupsApiToListData(groupsData?.data)
      const unassignedGroups = allUsers.filter(group => !originalGroups.find(original => original === group.id))
      return unassignedGroups
    } else return []
  }, [groupsData?.data, originalGroups])

  const rowCount = groupsData?.totalElements || 0
  const totalPages = Math.ceil(rowCount / pageSize)

  useEffect(() => {
    if (!open) {
      setSelection({})
    }
  }, [open])

  useEffect(() => {
    if (isPageIndexHigherThanTotalPages(pageIndex, totalPages)) {
      setPageIndex(totalPages - 1)
    }
  }, [pageIndex, totalPages])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const columnHelper = createColumnHelper<UserGroupsListData>()

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
          checked={row.getIsSelected()}
          onCheckedChange={value => {
            row.toggleSelected(!!value)
            setFocus(row.id)
          }}
          aria-label={`Select user ${row.original.name}`}
          id={row.id}
        />
      ),
      enableSorting: false,
      enableHiding: false,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('groupsTab.name')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('membersCount', {
      header: t('groupsTab.membersCount'),
      cell: info => info.getValue() || 0,
    }),
    columnHelper.accessor('contactUser', {
      header: t('groupsTab.contact'),
      cell: info => info.getValue()?.name || '-',
    }),
    columnHelper.accessor('description', {
      header: t('groupsTab.description'),
      cell: info => info.getValue() || '-',
      meta: {
        style: {
          whiteSpace: 'nowrap',
          maxWidth: '300px',
          textOverflow: 'ellipsis',
          overflow: 'hidden',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: groups,
    rowCount,
    state: {
      pagination: { pageIndex, pageSize },
      sorting,
      rowSelection: selection,
    },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      handlePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onRowSelectionChange: setSelection,
    onSortingChange: updater => setSorting(resolveUpdater(updater, sorting)),
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="block sm:max-w-[95%] sm:w-[95%] md:max-w-[1061px] h-[80%] max-h-[743px]  [--title-height:64px] [--button-height:60px]">
        <DialogHeader>
          <DialogTitle>{tCommon('actions.assignItem', { item: tCommon('items.group') })}</DialogTitle>
          <DialogDescription>{tCommon('actions.assignTo', { item: userName })}</DialogDescription>
        </DialogHeader>
        <SearchHeader
          searchString={searchString}
          onChangeSearchString={newSearchString => setSearchString(newSearchString)}
          className="my-2"
        />
        <div className="h-[calc(100%-var(--search-height)-var(--title-height)-var(--button-height))]">
          {isUpdating ? (
            <LoadingSpinner className="h-full" />
          ) : (
            <DataTable
              table={table}
              pageIndex={pageIndex}
              pageSize={pageSize}
              totalPages={totalPages}
              isLoading={isFetchingGroups}
            />
          )}
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={() => {
            onAssignGroups(selection)
            onOpenChange(false)
          }}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={isUpdating || isFetchingGroups}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
        />
      </DialogContent>
    </Dialog>
  )
}
