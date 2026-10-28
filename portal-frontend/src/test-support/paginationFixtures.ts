import { HttpCursorPagination, HttpPageNumberPagination } from '@/generated/core'
import { HttpCursorPaginationForm, HttpPaginationForm } from '@/types/connectors'

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

export const CURSOR_PAGINATION_FORM: HttpCursorPaginationForm = {
  isEnabled: true,
  type: 'CURSOR',
  cursorPath: '$.next_cursor',
  cursorParameter: 'cursor',
  cursorIn: 'QUERY',
  limitParameter: 'limit',
  limit: '20',
}

export const CURSOR_PAGINATION_API: HttpCursorPagination = {
  type: 'CURSOR',
  cursorPath: '$.next_cursor',
  cursorParameter: 'cursor',
  cursorIn: 'QUERY',
  limitParameter: 'limit',
  limit: 20,
}
