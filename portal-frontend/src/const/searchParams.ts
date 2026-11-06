export const QUERY_PARAMS = {
  pageIndex: 'page',
  pageSize: 'size',
  sort: 'sort',
  search: 'q',
  tabValue: 'tab',
  subTabValue: 'subtab',
} as const

/** The backend caps every page at this size (spring.data.web.pageable.max-page-size). */
export const MAX_PAGE_SIZE = 200

export type QueryParams = (typeof QUERY_PARAMS)[keyof typeof QUERY_PARAMS]

export const DATASET_FILTER_PARAMS = {
  datapoolIds: 'datapoolIds',
} as const

export const DATASOURCE_FILTER_PARAMS = {
  datapoolId: 'datapoolId',
  datapoolScopeType: 'datapoolScopeType',
  dataSourceStatus: 'dataSourceStatus',
} as const
