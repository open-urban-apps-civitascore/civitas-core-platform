import { PaginationState, SortingState } from '@tanstack/react-table'
import { ReadonlyURLSearchParams, usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'

import { QUERY_PARAMS } from '@/const/searchParams'

const getSortingState = (searchParams: ReadonlyURLSearchParams): SortingState => {
  const sorts = searchParams.getAll('sort')

  if (!sorts.length) return []

  return searchParams.getAll('sort').flatMap<SortingState[number]>(sort => {
    const [id, order] = sort.split(',')
    return !id
      ? []
      : [
          {
            id,
            desc: order?.toUpperCase() === 'DESC',
          },
        ]
  })
}

export const useQueryParams = () => {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const pageSize = useMemo(() => Number(searchParams.get(QUERY_PARAMS.pageSize) ?? 10), [searchParams])
  const pageIndex = useMemo(
    () => (Number(searchParams.get(QUERY_PARAMS.pageIndex)) ? Number(searchParams.get(QUERY_PARAMS.pageIndex)) : 0),
    [searchParams],
  )
  const sorting: SortingState = useMemo(() => getSortingState(searchParams), [searchParams])
  const search = useMemo(() => searchParams.get(QUERY_PARAMS.search) ?? '', [searchParams])
  const tabValue = useMemo(() => searchParams.get(QUERY_PARAMS.tabValue) ?? '', [searchParams])
  const subTabValue = useMemo(() => searchParams.get(QUERY_PARAMS.subTabValue) ?? '', [searchParams])

  const [totalPages, setTotalPages] = useState(0)

  useEffect(() => {
    if (totalPages && totalPages > 0 && pageIndex + 1 > totalPages) {
      setPaginationParams({ pageIndex: totalPages - 1, pageSize: pageSize })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [totalPages, pageIndex, pageSize])

  const setSortingParams = (newSorting: SortingState) => {
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

  const setSearchParam = (newSearch: string) => {
    const params = new URLSearchParams(searchParams)
    if (newSearch) {
      params.set(QUERY_PARAMS.search, newSearch)
    } else {
      params.delete(QUERY_PARAMS.search)
    }
    router.push(`${pathname}?${params.toString()}`)
  }

  const setPaginationParams = useCallback(
    (newPagination: PaginationState) => {
      const params = new URLSearchParams(searchParams)
      if (newPagination) {
        params.set(QUERY_PARAMS.pageIndex, String(newPagination.pageIndex))
        params.set(QUERY_PARAMS.pageSize, String(newPagination.pageSize))
      }
      router.push(`${pathname}?${params.toString()}`)
    },
    [pathname, router, searchParams],
  )

  const setTabValueParam = (tabValue: string) => {
    const params = new URLSearchParams(searchParams)
    if (tabValue) {
      params.set(QUERY_PARAMS.tabValue, tabValue)
    } else {
      params.delete(QUERY_PARAMS.tabValue)
    }
    router.push(`${pathname}?${params.toString()}`)
  }

  const setSubTabValueParam = (subTabValue: string) => {
    const params = new URLSearchParams(searchParams)
    if (subTabValue) {
      params.set(QUERY_PARAMS.subTabValue, subTabValue)
    } else {
      params.delete(QUERY_PARAMS.subTabValue)
    }
    router.push(`${pathname}?${params.toString()}`)
  }

  type ApiRequestParams = {
    pageIndex: number
    pageSize: number
    search?: string
    sorting?: SortingState
  }

  const getApiRequestParams = useCallback((params: ApiRequestParams): URLSearchParams => {
    const apiParams = new URLSearchParams()

    apiParams.set(QUERY_PARAMS.pageIndex, String(params.pageIndex))
    apiParams.set(QUERY_PARAMS.pageSize, String(params.pageSize))

    if (params.sorting && params.sorting.length > 0) {
      params.sorting.forEach(sort => {
        const sortValue = `${sort.id},${sort.desc ? 'DESC' : 'ASC'}`
        apiParams.append('sort', sortValue)
      })
    }

    if (params.search && params.search.trim().length > 0) {
      apiParams.set(QUERY_PARAMS.search, params.search.trim())
    }

    return apiParams
  }, [])

  const getApiRequestParamsByUrl = useCallback(() => {
    return getApiRequestParams({
      pageIndex,
      pageSize,
      search,
      sorting,
    })
  }, [pageIndex, pageSize, search, sorting, getApiRequestParams])

  return {
    setSearchParam,
    setSortingParams,
    setPaginationParams,
    getApiRequestParams,
    getApiRequestParamsByUrl,
    setTabValueParam,
    setSubTabValueParam,
    setTotalPages,
    pageIndex,
    pageSize,
    sorting,
    search,
    tabValue,
    subTabValue,
    totalPages,
  }
}
