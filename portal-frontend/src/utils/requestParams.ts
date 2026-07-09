import { QUERY_PARAMS, QueryParams } from '@/const/searchParams'

/* eslint-disable @typescript-eslint/naming-convention */
export type ApiRequestParams = Partial<Omit<Record<QueryParams, string>, typeof QUERY_PARAMS.sort>> & {
  [QUERY_PARAMS.sort]?: string | string[]
  [key: string]: string | string[] | undefined
}

export type JsonServerRequestParams = {
  _page?: string
  _limit?: string
  _sort?: string | string[]
  _order?: string
  q?: string
}

export const getApiRequestParams = (params: ApiRequestParams) => {
  const pageIndex = parseInt(params.page || '0')
  const pageSize = parseInt(params.size || '10')
  const sort = params.sort ? [params.sort].flatMap(entry => entry) : []
  const search = params.q || ''

  const apiParams = new URLSearchParams()
  apiParams.set(QUERY_PARAMS.pageIndex, String(pageIndex))
  apiParams.set(QUERY_PARAMS.pageSize, String(pageSize))

  sort.forEach(s => apiParams.append(QUERY_PARAMS.sort, s))

  if (search) {
    apiParams.set(QUERY_PARAMS.search, search)
  }

  return { apiParams, pageIndex, pageSize, sort, search }
}

export const getJsonServerRequestParams = (params: JsonServerRequestParams) => {
  const pageIndex = parseInt(params._page || '0')
  const pageSize = parseInt(params._limit || '10')
  const sort = params._sort
  const order = params._order
  const search = params.q || ''

  const jsonServerParams = new URLSearchParams()
  jsonServerParams.set('_page', String(pageIndex))
  jsonServerParams.set('_limit', String(pageSize))

  if (sort) jsonServerParams.set('_sort', String(sort))
  if (order) jsonServerParams.set('_order', String(order))
  if (search) jsonServerParams.set('q', search)

  return { jsonServerParams, pageIndex, pageSize, sort, order, search }
}
