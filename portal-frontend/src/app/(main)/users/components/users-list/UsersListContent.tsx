'use client'

import { PaginationState, Row, RowSelectionState, SortingState, Updater } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { Button } from '@/components/ui/button'
import { ListUser } from '@/types/users'

import UsersTable from './UsersTable'

type Props = {
  users: ListUser[]
  totalCount: number
  pageIndex: number
  pageSize: number
  sorting: SortingState
  totalPages: number
  search: string
  newUserLabel: string
}

export const UsersListContent = ({
  users,
  totalCount,
  pageIndex,
  pageSize,
  sorting: initialSorting,
  totalPages,
  search,
  newUserLabel,
}: Props) => {
  const router = useRouter()
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const handleRowClick = (row: Row<ListUser>) => {
    if (row.id) {
      const params = new URLSearchParams(window.location.search)
      router.push(`users/${row.id}?${params.toString()}`)
    }
  }

  const handleSortingChange = (updater: Updater<SortingState>) => {
    const newSorting = typeof updater === 'function' ? updater(initialSorting) : updater
    const params = new URLSearchParams(window.location.search)

    if (newSorting.length > 0) {
      const sort = [`${newSorting[0].id},${newSorting[0].desc ? 'DESC' : 'ASC'}`]
      params.set('sort', sort.toString())
    } else {
      params.delete('sort')
    }

    router.push(`?${params.toString()}`)
  }

  const handlePaginationChange = (updater: Updater<PaginationState>) => {
    const currentState = { pageIndex, pageSize }
    const newState = typeof updater === 'function' ? updater(currentState) : updater
    const params = new URLSearchParams(window.location.search)

    params.set('page', String(newState.pageIndex))
    params.set('pageSize', String(newState.pageSize))

    router.push(`?${params.toString()}`)
  }

  const handleSearchChange = (searchString: string) => {
    const params = new URLSearchParams(window.location.search)

    if (searchString) {
      params.set('search', searchString)
    } else {
      params.delete('search')
    }
    params.set('page', '0') // Reset to first page on search

    router.push(`?${params.toString()}`)
  }

  const CustomElement = (
    <Button data-testid="addUserButton" onClick={() => router.push('/users/create')}>
      <Plus />
      {newUserLabel}
    </Button>
  )

  return (
    <>
      <SearchHeader customElement={CustomElement} onChangeSearchString={handleSearchChange} searchString={search} />
      <UsersTable
        users={users}
        rowCount={totalCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        sorting={initialSorting}
        totalPages={totalPages}
        rowSelection={rowSelection}
        setRowSelection={setRowSelection}
        onRowClick={handleRowClick}
        onSortingChange={handleSortingChange}
        onPaginationChange={handlePaginationChange}
        isLoading={false}
      />
    </>
  )
}
