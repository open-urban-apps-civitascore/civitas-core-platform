import { SortingState } from '@tanstack/react-table'
import { Dispatch, SetStateAction } from 'react'

export interface TableProps {
  rowCount: number
  pageSize: number
  totalPages: number
  setPageSize: Dispatch<SetStateAction<number>>
  pageIndex: number
  setPageIndex: Dispatch<SetStateAction<number>>
  sorting: SortingState
  setSorting: Dispatch<SetStateAction<SortingState>>
}
