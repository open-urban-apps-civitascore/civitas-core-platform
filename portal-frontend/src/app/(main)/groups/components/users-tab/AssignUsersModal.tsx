import { DialogProps } from '@radix-ui/react-dialog'
import {
  createColumnHelper,
  getCoreRowModel,
  PaginationState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { useGetUsers } from '@/app/services/api/users/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useAddItemSelection } from '@/hooks/use-add-item-selection'
import { useQueryParams } from '@/hooks/use-query-params'
import { ListUser } from '@/types/users'
import { isPageIndexHigherThanTotalPages, resolveUpdater } from '@/utils/table'
import { mapListUsers } from '@/utils/users'

interface AssignUsersModalProps extends DialogProps {
  assignedFormUsers: string[]
  groupTitle: string
  onAssignUsers: (userIds: string[]) => void
  isUpdating?: boolean
}

export const AssignUsersModal = (props: AssignUsersModalProps) => {
  const { assignedFormUsers, groupTitle, open, onOpenChange = () => {}, onAssignUsers, isUpdating = false } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const tUsers = useTranslations('users')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const { getApiRequestParams } = useQueryParams()

  const { data: usersData, isFetching: isFetchingUsers } = useGetUsers({
    params: getApiRequestParams({ pageIndex, pageSize, sorting, search: searchString }),
  })

  const users = useMemo(() => {
    if (usersData?.data && usersData?.data.length > 0) {
      return mapListUsers(usersData?.data)
    } else return []
  }, [usersData?.data])

  const rowCount = usersData?.totalElements || 0
  const totalPages = Math.ceil(rowCount / pageSize)

  const { selection, selectedItemsRef, assignedIdsSet, handleSelectionChange, newlySelectedCount } =
    useAddItemSelection({ assignedIds: assignedFormUsers, items: users, open: !!open })

  useEffect(() => {
    if (!open) {
      setSearchString('')
      setPageIndex(0)
      setSorting([])
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

  const setFocus = (elementId: string) => {
    requestAnimationFrame(() => {
      const element = document.getElementById(elementId)
      element?.focus()
    })
  }

  const columnHelper = createColumnHelper<ListUser>()

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
              row.toggleSelected(!!value)
              setFocus(row.id)
            }}
            disabled={isAssigned}
            aria-label={`Select user ${row.original.fullName}`}
            id={row.id}
          />
        )
      },
      enableSorting: false,
      enableHiding: false,
    }),
    columnHelper.accessor('fullName', {
      header: ({ column }) => <SortableTableHeader column={column} title={tUsers('info.displayName')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('email', {
      header: ({ column }) => <SortableTableHeader column={column} title={tUsers('info.email')} />,
      cell: info => info.getValue(),
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: users,
    rowCount,
    state: {
      pagination: { pageIndex, pageSize },
      sorting,
      rowSelection: selection,
    },
    enableRowSelection: row => !assignedIdsSet.has(row.original.id),
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    onPaginationChange: updater => {
      handlePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onRowSelectionChange: handleSelectionChange,
    onSortingChange: updater => setSorting(resolveUpdater(updater, sorting)),
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="block sm:max-w-[95%] sm:w-[95%] md:max-w-[1061px] h-[80%] max-h-[743px]  [--title-height:64px] [--button-height:60px]">
        <DialogHeader>
          <DialogTitle>{t('users.assign')}</DialogTitle>
          <DialogDescription>
            {t('users.toGroup')} {groupTitle}
          </DialogDescription>
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
              isLoading={isFetchingUsers}
            />
          )}
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={() => onAssignUsers(Array.from(selectedItemsRef.current.keys()))}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={isUpdating || newlySelectedCount === 0}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
        />
      </DialogContent>
    </Dialog>
  )
}
