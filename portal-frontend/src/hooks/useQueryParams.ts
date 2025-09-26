import { PaginationState, SortingState } from '@tanstack/react-table'
import { ReadonlyURLSearchParams, usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useMemo } from 'react'

import { QUERY_PARAMS } from '@/const/searchParams'

const getSortingState = (searchParams: ReadonlyURLSearchParams) => {
  const sorting = searchParams.get(QUERY_PARAMS.sortingId)
    ? [
        {
          id: searchParams.get(QUERY_PARAMS.sortingId) as string,
          desc: searchParams.get(QUERY_PARAMS.order) === 'desc' ? true : false,
        },
      ]
    : []
  return sorting
}

export const useQueryParams = () => {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const pageSize = useMemo(() => Number(searchParams.get(QUERY_PARAMS.pageSize) ?? 10), [searchParams])
  const pageIndex = useMemo(
    () => (Number(searchParams.get(QUERY_PARAMS.pageIndex)) ? Number(searchParams.get(QUERY_PARAMS.pageIndex)) : 1),
    [searchParams],
  )
  const sorting: SortingState = useMemo(() => getSortingState(searchParams), [searchParams])
  const search = useMemo(() => searchParams.get(QUERY_PARAMS.search) ?? '', [searchParams])

  const setSortingParams = (newSorting: SortingState) => {
    const params = new URLSearchParams(searchParams)
    const sortingId = newSorting[0]?.id
    const order = newSorting[0]?.desc ? `desc` : `asc`
    if (sortingId && order) {
      params.set(QUERY_PARAMS.sortingId, String(sortingId))
      params.set(QUERY_PARAMS.order, String(order))
    }
    router.push(`${pathname}?${params.toString()}`)
  }

  const setSearchParam = (newSearch: string) => {
    const params = new URLSearchParams(searchParams)
    params.set(QUERY_PARAMS.search, newSearch)
    router.push(`${pathname}?${params.toString()}`)
  }

  const setPaginationParams = (newPagination: PaginationState) => {
    const params = new URLSearchParams(searchParams)
    if (newPagination) {
      params.set(QUERY_PARAMS.pageIndex, String(newPagination.pageIndex + 1))
      params.set(QUERY_PARAMS.pageSize, String(newPagination.pageSize))
    }
    router.push(`${pathname}?${params.toString()}`)
  }

  return { setSearchParam, setSortingParams, setPaginationParams, pageIndex, pageSize, sorting, search }
}
