export const QUERY_PARAMS = {
  pageIndex: 'page',
  pageSize: 'size',
  sort: 'sort',
  search: 'q',
  tabValue: 'tab',
  subTabValue: 'subtab',
} as const

export type QueryParams = (typeof QUERY_PARAMS)[keyof typeof QUERY_PARAMS]

export const QUERY_PARAMS_JSON_SERVER = {
  pageIndex: '_page',
  pageSize: '_limit',
  sortingId: '_sort',
  order: '_order',
  search: 'q',
  tabValue: '_tab',
  subTabValue: '_subtab',
}
