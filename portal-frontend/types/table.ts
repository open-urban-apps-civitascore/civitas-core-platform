import { PaginationState, Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Dispatch, SetStateAction } from 'react'

export interface TableProps<T> {
  rowCount: number
  pageSize: number
  totalPages: number
  setPageSize?: Dispatch<SetStateAction<number>>
  pageIndex: number
  setPageIndex?: Dispatch<SetStateAction<number>>
  sorting: SortingState
  setSorting?: Dispatch<SetStateAction<SortingState>>
  rowSelection?: RowSelectionState
  setRowSelection?: Dispatch<SetStateAction<RowSelectionState>>
  onRowClick?: (row: Row<T>) => void
  onCellClick?: (row: Row<T>, columnId: keyof T) => void
  isCellClickable?: (row: Row<T>, columnId: string) => boolean
  onPaginationChange: (newPagination: PaginationState) => void
  onSortingChange: (newSorting: SortingState) => void
  isLoading?: boolean
}
