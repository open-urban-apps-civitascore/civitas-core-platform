import { DialogProps } from '@radix-ui/react-dialog'
import {
  createColumnHelper,
  getCoreRowModel,
  getSortedRowModel,
  PaginationState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { Check } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { QUERY_PARAMS } from '@/const/searchParams'
import { GroupAssignmentUser, UserResponse } from '@/types/users'
import { resolveUpdater } from '@/utils/table'
import { mapGoupAssignmentUsers } from '@/utils/users'

export type UserSelection = { selectAll: boolean; selectedIds: string[]; excludedIds: string[] }

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`
const reduceValues = (values: string[]) =>
  values.reduce(
    (acc, id) => {
      acc[id] = true
      return acc
    },
    {} as Record<string, boolean>,
  )

interface AssignUsersModalProps extends DialogProps {
  originalUsers: { id: string; assignedAt: string }[]
  groupTitle: string
  onUpdateUsers: (userSelection: UserSelection) => void
  isUpdating?: boolean
}

export const AssignUsersModal = (props: AssignUsersModalProps) => {
  const { originalUsers, groupTitle, open, onOpenChange = () => {}, onUpdateUsers, isUpdating = false } = props
  const t = useTranslations('groups')
  const tUsers = useTranslations('users')
  const [users, setUsers] = useState<GroupAssignmentUser[]>([])
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [isLoadingUsersList, setIsLoadingUsersList] = useState(true)
  const [sorting, setSorting] = useState<SortingState>([])
  const [rowCount, setRowCount] = useState(0)
  const [searchString, setSearchString] = useState('')
  const [selection, setSelection] = useState<UserSelection>({ selectAll: false, selectedIds: [], excludedIds: [] })
  const totalPages = Math.ceil(rowCount / pageSize)

  const getRequestParams = () => {
    const requestParams = new URLSearchParams()
    requestParams.set(QUERY_PARAMS.pageSize, pageSize.toString())
    requestParams.set(QUERY_PARAMS.pageIndex, (pageIndex + 1).toString())
    if (sorting[0]) {
      requestParams.set(QUERY_PARAMS.sortingId, sorting[0].id)
      requestParams.set(QUERY_PARAMS.order, sorting[0].desc ? 'desc' : 'asc')
    }
    return requestParams
  }

  // this implementation has to be adjusted when the backend is implemented
  // only unassigned users have to be returned from the backend directly
  const getUserListData = async () => {
    const requestParams = getRequestParams()
    try {
      const usersResponse = await fetch(`${URL}/users?${requestParams.toString()}`, {
        cache: 'no-store',
      })
      if (!usersResponse.ok) {
        throw new Error('An error occurred while loading form data')
      }

      const usersData: UserResponse[] = await usersResponse.json()
      const allUsers = mapGoupAssignmentUsers(usersData)
      const unassignedUsers = allUsers.filter(user => !originalUsers.find(original => original.id === user.id))
      setUsers(unassignedUsers)

      const totalCount = Number(usersResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }
    } catch (error) {
      console.error('An error occurred while fetching users data:', error)
    } finally {
      setIsLoadingUsersList(false)
    }
  }

  useEffect(() => {
    getUserListData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, rowCount, sorting, totalPages, searchString, originalUsers])

  const hanldePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const handleToggleCheckAll = () => {
    setSelection(prev => ({
      selectAll: !prev.selectAll,
      selectedIds: [],
      excludedIds: [],
    }))
  }

  const columnHelper = createColumnHelper<GroupAssignmentUser>()

  const columns = [
    columnHelper.accessor('id', {
      header: () => (
        <Checkbox
          checked={
            (selection.selectAll && selection.excludedIds.length === 0) ||
            (!selection.selectAll && selection.selectedIds.length > 0 && 'indeterminate') ||
            (selection.selectAll && selection.excludedIds.length > 0 && 'indeterminate')
          }
          onCheckedChange={handleToggleCheckAll}
          aria-label="Select all"
        />
      ),
      cell: ({ row }) => (
        <Checkbox
          checked={row.getIsSelected()}
          onCheckedChange={value => row.toggleSelected(!!value)}
          aria-label="Select row"
        />
      ),
      enableSorting: false,
      enableHiding: false,
    }),
    columnHelper.accessor('displayName', {
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
    columnHelper.accessor('isActive', {
      header: tUsers('info.active'),
      cell: info => (info.getValue() ? <Check /> : '-'),
    }),
  ]

  const handleRowSelectionChange = (newSelection: Record<string, boolean>) => {
    let selectedIds: string[] = []
    let excludedIds: string[] = []
    if (selection.selectAll) {
      const excludedUsers = users.filter(user => !newSelection[user.id])
      excludedIds = excludedUsers.map(user => user.id)
    } else {
      selectedIds = Object.keys(newSelection).filter(id => newSelection[id])
    }
    setSelection(prev => ({
      ...prev,
      selectedIds,
      excludedIds,
    }))
  }

  const rowSelection = useMemo<Record<string, boolean>>(() => {
    if (selection.selectAll) {
      const selectedUsers = users.filter(user => !selection.excludedIds.find(id => id === user.id))
      return reduceValues(selectedUsers.map(u => u.id))
    }
    if (selection.selectedIds.length > 0) {
      return reduceValues(selection.selectedIds)
    }
    return {}
  }, [selection, users])

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: users,
    rowCount,
    state: {
      pagination: { pageIndex, pageSize },
      sorting,
      rowSelection: rowSelection,
    },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      hanldePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onRowSelectionChange: updater => handleRowSelectionChange(resolveUpdater(updater, rowSelection)),
    onSortingChange: updater => setSorting(resolveUpdater(updater, sorting)),
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="block sm:max-w-[95%] sm:w-[95%] md:max-w-[1061px] h-[80%] max-h-[743px]  [--title-height:64px] [--actionbutton-height:62px]">
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
        <div className="h-[calc(100%-var(--search-height)-var(--title-height)-var(--actionbutton-height))]">
          {isUpdating ? (
            <LoadingSpinner className="h-full" />
          ) : (
            <DataTable
              table={table}
              pageIndex={pageIndex}
              pageSize={pageSize}
              totalPages={totalPages}
              isLoading={isLoadingUsersList}
            />
          )}
        </div>
        <ActionButtons
          confirmButtonType="button"
          onCancelClick={() => onOpenChange(false)}
          onConfirmClick={() => onUpdateUsers(selection)}
          hasCard={false}
          isCancelButtonDisabled={isUpdating || isLoadingUsersList}
          isConfirmButtonDisabled={isUpdating || isLoadingUsersList}
        />
      </DialogContent>
    </Dialog>
  )
}
