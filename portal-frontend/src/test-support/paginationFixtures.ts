import { HttpPageNumberPagination } from '@/generated/core'
import { HttpPaginationForm } from '@/types/connectors'

export const PAGINATION_FORM: HttpPaginationForm = {
  isEnabled: true,
  type: 'PAGE_NUMBER',
  pageParameter: 'page',
  pageSizeParameter: 'size',
  pageSize: '100',
  startPage: '1',
  endPage: '10',
}

export const PAGINATION_API: HttpPageNumberPagination = {
  type: 'PAGE_NUMBER',
  pageParameter: 'page',
  pageSizeParameter: 'size',
  pageSize: 100,
  startPage: 1,
  endPage: 10,
}
