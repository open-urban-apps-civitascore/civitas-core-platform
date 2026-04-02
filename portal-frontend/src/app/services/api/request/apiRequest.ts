import { AxiosError, AxiosRequestConfig, Method } from 'axios'

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
