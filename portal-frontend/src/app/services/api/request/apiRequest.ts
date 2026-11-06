import { AxiosError, AxiosRequestConfig, Method } from 'axios'

import { MAX_PAGE_SIZE, QUERY_PARAMS } from '@/const/searchParams'

import { axiosClient } from '../client/client'

type FetchConfig<TBody = unknown> = {
  endpoint: string
  params?: URLSearchParams
  headers?: AxiosRequestConfig['headers']
  errorMessage?: string
  method: Method
  data?: TBody
}

type APIResponseMeta = Partial<{
  totalElements: number
  totalPages: number
  last: boolean
  first: boolean
  numberOfElements: number
  size: number
  number: number
  empty: boolean
  pageable: {
    pageNumber: number
    pageSize: number
    sort: {
      unsorted: boolean
      sorted: boolean
      empty: boolean
    }
    offset: number
    unpaged: boolean
    paged: boolean
  }
  sort: {
    unsorted: boolean
    sorted: boolean
    empty: boolean
  }
}>
// The Partial<> wrapper can be removec once we switch to correct API
export type ApiResponse<T = unknown> = {
  content: T
} & APIResponseMeta

export type ApiServiceResponse<T = unknown> = { data: T; totalElements?: number; totalPages?: number }

export const apiRequest = async <TResponse, TBody = unknown>({
  endpoint,
  params,
  headers: requestHeaders,
  method,
  errorMessage,
  data,
}: FetchConfig<TBody>): Promise<ApiServiceResponse<TResponse>> => {
  try {
    const paramsString = params?.toString()
    const url = paramsString ? `/api${endpoint}?${paramsString}` : `/api${endpoint}`

    const response = await axiosClient.request<ApiResponse<TResponse>>({
      url,
      headers: {
        ...requestHeaders,

        'Cache-Control': 'no-store',
      },
      method,
      data,
    })

    return {
      data: response.data.content,
      totalElements: response.data.totalElements,
      totalPages: response.data.totalPages,
    }
  } catch (error: unknown) {
    const axiosError = error as AxiosError
    console.error(`${errorMessage || 'Failed to fetch data.'} ${axiosError.status}: ${axiosError.message}`)
    throw axiosError
  }
}

/** Requests every page of a list endpoint and returns all items in one response. */
export const apiRequestAllPages = async <TItem>(
  config: Omit<FetchConfig<never>, 'params' | 'method' | 'data'>,
): Promise<ApiServiceResponse<TItem[]>> => {
  const items: TItem[] = []
  let pageIndex = 0
  let totalPages = 1
  while (pageIndex < totalPages) {
    const params = new URLSearchParams({
      [QUERY_PARAMS.pageIndex]: String(pageIndex),
      [QUERY_PARAMS.pageSize]: String(MAX_PAGE_SIZE),
    })
    const page = await apiRequest<TItem[]>({ ...config, method: 'GET', params })
    items.push(...page.data)
    totalPages = page.totalPages ?? 1
    pageIndex += 1
  }
  return { data: items, totalElements: items.length, totalPages: 1 }
}
