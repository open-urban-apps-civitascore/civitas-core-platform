export const QUERY_PARAMS = {
  pageIndex: 'page',
  pageSize: 'size',
  sort: 'sort',
  search: 'q',
  tabValue: 'tab',
  subTabValue: 'subtab',
} as const

export type QueryParams = (typeof QUERY_PARAMS)[keyof typeof QUERY_PARAMS]
