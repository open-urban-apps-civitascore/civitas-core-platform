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
import { useEffect, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { QUERY_PARAMS } from '@/const/searchParams'
import { GroupAssignmentUser, UserResponse } from '@/types/users'
import { resolveUpdater } from '@/utils/table'
import { mapGoupAssignmentUsers } from '@/utils/users'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

interface AssignUsersModalProps extends DialogProps {
  originalUsers: { id: string; assignedAt: string }[]
  groupTitle: string
}

export const AssignUsersModal = (props: AssignUsersModalProps) => {
  const { originalUsers, groupTitle, open, onOpenChange = () => {} } = props
  const t = useTranslations('groups')
  const tUsers = useTranslations('users')
  const [users, setUsers] = useState<GroupAssignmentUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([])
  const [rowCount, setRowCount] = useState(0)
  const [searchString, setSearchString] = useState('')
  const totalPages = Math.ceil(rowCount / pageSize)

  const getRequestParams = () => {
    const requestParams = new URLSearchParams()
    requestParams.set(QUERY_PARAMS.pageSize, pageSize.toString())
    requestParams.set(QUERY_PARAMS.pageIndex, pageIndex.toString())
    if (sorting[0]) {
      requestParams.set(QUERY_PARAMS.sortingId, sorting[0].id)
      requestParams.set(QUERY_PARAMS.order, sorting[0].desc ? 'desc' : 'asc')
    }
    return requestParams
  }

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
      setIsLoading(false)
    }
  }

  useEffect(() => {
    getUserListData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, rowCount, sorting, totalPages])

  const hanldePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const columnHelper = createColumnHelper<GroupAssignmentUser>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
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
      header: t('info.active'),
      cell: info => (info.getValue() ? <Check /> : '-'),
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: users,
    rowCount,
    initialState: {
      columnVisibility: {
        id: false,
      },
    },
    state: { pagination: { pageIndex, pageSize }, sorting },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: updater => {
      hanldePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
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
          onCancelClick={() => onOpenChange(true)}
          onConfirmClick={() => {}}
          hasCard={false}
        />
      </DialogContent>
    </Dialog>
  )
}
