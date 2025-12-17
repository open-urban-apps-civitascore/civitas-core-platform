export type RequestParams = {
  page?: string
  pageSize?: string
  sort?: string | string[]
  order?: string
  q?: string
}

export const getRequestParams = (params: RequestParams) => {
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
