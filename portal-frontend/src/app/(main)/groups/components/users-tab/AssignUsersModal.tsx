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
import { useEffect, useRef, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { StatusLabel } from '@/components/status-label/StatusLabel'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useQueryParams } from '@/hooks/useQueryParams'
import { GroupAssignmentUser, UserResponse } from '@/types/users'
import { isPageIndexHigherThanTotalPages, resolveUpdater } from '@/utils/table'
import { mapGoupAssignmentUsers } from '@/utils/users'

export type UserSelection = { selectAll: boolean; selectedIds: string[]; excludedIds: string[] }

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

interface AssignUsersModalProps extends DialogProps {
  originalUsers: { id: string; assignedAt: string }[]
  groupTitle: string
  onUpdateUsers: (userSelection: RowSelectionState) => void
  isUpdating?: boolean
}

export const AssignUsersModal = (props: AssignUsersModalProps) => {
  const { originalUsers, groupTitle, open, onOpenChange = () => {}, onUpdateUsers, isUpdating = false } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const tUsers = useTranslations('users')
  const [users, setUsers] = useState<GroupAssignmentUser[]>([])
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [isLoading, setIsLoading] = useState(true)
  const [sorting, setSorting] = useState<SortingState>([])
  const [rowCount, setRowCount] = useState(0)
  const [searchString, setSearchString] = useState('')
  const [selection, setSelection] = useState<RowSelectionState>({})
  const totalPages = Math.ceil(rowCount / pageSize)
  const selectAllCheckbox = useRef<HTMLButtonElement>(null)
  const { getApiRequestParams } = useQueryParams()

  // this implementation has to be adjusted when the backend is implemented
  // only unassigned users have to be returned from the backend directly
  const getUserListData = async () => {
    const requestParams = getApiRequestParams({ pageIndex, pageSize, sorting, search: searchString })
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
      setIsLoading(false)
    }
  }

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

  useEffect(() => {
    setIsLoading(true)
    getUserListData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, rowCount, sorting, searchString, originalUsers])

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

  const columnHelper = createColumnHelper<GroupAssignmentUser>()

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
          aria-label={`Select user ${row.original.fullName}`}
          id={row.id}
        />
      ),
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
    columnHelper.accessor('isActive', {
      header: tUsers('info.active'),
      cell: info => <StatusLabel isChecked={info.getValue()} />,
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
              isLoading={isLoading}
            />
          )}
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={() => onUpdateUsers(selection)}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={isUpdating || isLoading}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
        />
      </DialogContent>
    </Dialog>
  )
}
