import { PaginationState, SortingState } from '@tanstack/react-table'
import { useRouter, useSearchParams } from 'next/navigation'

/**
 * Provides URL-based table state handlers for server component pages.
 * Sorting, pagination, and search changes are pushed as URL search params,
 * triggering a server-side re-fetch.
 */
export const useTableSearchParams = () => {
  const router = useRouter()
  const searchParams = useSearchParams()

  const handleSortingChange = (newSorting: SortingState) => {
    const params = new URLSearchParams(searchParams.toString())

    const existingSorts = params.getAll('sort')
    params.delete('sort')

    newSorting.forEach(sort => {
      const newSortStr = `${sort.id},${sort.desc ? 'DESC' : 'ASC'}`
      const existingIndex = existingSorts.findIndex(existingSort => existingSort.startsWith(`${sort.id}`))

      if (existingIndex >= 0) {
        existingSorts[existingIndex] = newSortStr
      } else {
        existingSorts.push(newSortStr)
      }
    })
    existingSorts.forEach(existingSort => params.append('sort', existingSort))
    router.push(`?${params.toString()}`)
  }

  const handlePaginationChange = (newState: PaginationState) => {
    const params = new URLSearchParams(searchParams.toString())

    params.set('page', String(newState.pageIndex))
    params.set('pageSize', String(newState.pageSize))

    router.push(`?${params.toString()}`)
  }

  const handleSearchChange = (searchString: string) => {
    const params = new URLSearchParams(searchParams.toString())
    if (searchString) params.set('q', searchString)
    else params.delete('q')
    params.set('page', '0')
    router.push(`?${params.toString()}`)
  }

  return { handleSortingChange, handlePaginationChange, handleSearchChange }
}
