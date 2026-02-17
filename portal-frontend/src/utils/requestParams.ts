/* eslint-disable @typescript-eslint/naming-convention */
export type ApiRequestParams = {
  page?: string
  pageSize?: string
  sort?: string | string[]
  order?: string
  q?: string
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
  const pageSize = parseInt(params.pageSize || '10')
  const sort = params.sort ? [params.sort].flatMap(entry => entry) : []
  const search = params.q || ''

  const apiParams = new URLSearchParams()
  apiParams.set('page', String(pageIndex))
  apiParams.set('size', String(pageSize))

  sort.forEach(s => apiParams.append('sort', s))

  if (search) {
    apiParams.set('q', search)
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
