import { AxiosRequestConfig, Method } from 'axios'

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
    const response = await axiosClient.request<ApiResponse<TResponse>>({
      url: `/api${endpoint}?${params?.toString() || ''}`,
      headers: {
        ...requestHeaders,
        /* eslint-disable @typescript-eslint/naming-convention */
        'Cache-Control': 'no-store',
        'x-api-request': 'true',
        /* eslint-enable @typescript-eslint/naming-convention */
      },
      method,
      data,
    })

    return {
      data: response.data.content,
      totalElements: response.data.totalElements,
      totalPages: response.data.totalPages,
    }
  } catch (error) {
    console.error(`${errorMessage} ${error}` || `Failed to fetch data. ${error}`)
    throw new Error(`${errorMessage} ${error}` || `Failed to fetch data. ${error}`)
  }
}
