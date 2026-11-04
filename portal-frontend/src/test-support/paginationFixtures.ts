import { HttpCursorPagination, HttpLimitOffsetPagination, HttpPageNumberPagination } from '@/generated/core'
import { HttpCursorPaginationForm, HttpLimitOffsetPaginationForm, HttpPaginationForm } from '@/types/httpApiCalls'

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

export const LIMIT_OFFSET_PAGINATION_FORM: HttpLimitOffsetPaginationForm = {
  isEnabled: true,
  type: 'LIMIT_OFFSET',
  offsetParameter: 'offset',
  offset: '80',
  limitParameter: 'limit',
  limit: '20',
  countPath: '$.meta.count',
}

export const LIMIT_OFFSET_PAGINATION_API: HttpLimitOffsetPagination = {
  type: 'LIMIT_OFFSET',
  offsetParameter: 'offset',
  offset: 80,
  limitParameter: 'limit',
  limit: 20,
  countPath: '$.meta.count',
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
