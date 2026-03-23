'use client'

import { PaginationState, RowSelectionState, SortingState, Updater } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { SearchHeader } from '@/components/search-area/SearchArea'
import { Button } from '@/components/ui/button'
import { QUERY_PARAMS } from '@/const/searchParams'
import { ListUser } from '@/types/users'

import UsersTable from './UsersTable'

interface UsersListContentProps {
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
}: UsersListContentProps) => {
  const router = useRouter()
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})

  const handleSortingChange = (updater: Updater<SortingState>) => {
    const newSorting = typeof updater === 'function' ? updater(initialSorting) : updater
    const params = new URLSearchParams(window.location.search)

    const existingSorts = params.getAll('sort')
    params.delete('sort')

    newSorting.forEach(sort => {
      const newSortStr = `${sort.id},${sort.desc ? 'DESC' : 'ASC'}`
      const existingIndex = existingSorts.findIndex(existingSort => existingSort.startsWith(`${sort.id}`))

      if (existingIndex >= 0) {
        // if sort for this id already exists, replace it
        existingSorts[existingIndex] = newSortStr
      } else {
        existingSorts.push(newSortStr)
      }
    })
    existingSorts.forEach(existingSort => params.append('sort', existingSort))
    router.push(`?${params.toString()}`)
  }

  const handlePaginationChange = (updater: Updater<PaginationState>) => {
    const currentState = { pageIndex, pageSize }
    const newState = typeof updater === 'function' ? updater(currentState) : updater
    const params = new URLSearchParams(window.location.search)

    params.set(QUERY_PARAMS.pageIndex, String(newState.pageIndex))
    params.set(QUERY_PARAMS.pageSize, String(newState.pageSize))

    router.push(`?${params.toString()}`)
  }

  const handleSearchChange = (searchString: string) => {
    const params = new URLSearchParams(window.location.search)
    const queryParts = Array.from(params.entries()).flatMap(([key, value]) =>
      key === 'q' || key === 'page' ? [] : `${key}=${encodeURIComponent(value)}`,
    )

    if (searchString) {
      queryParts.push(`q=${encodeURIComponent(searchString)}`)
    }
    queryParts.push(`page=0`) // reset to first page on search

    router.push(`?${queryParts.join('&')}`)
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
        onSortingChange={handleSortingChange}
        onPaginationChange={handlePaginationChange}
      />
    </>
  )
}
