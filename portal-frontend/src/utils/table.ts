import { Updater } from '@tanstack/react-table'

export const resolveUpdater = <T>(updater: Updater<T>, old: T): T => {
  return typeof updater === 'function' ? (updater as (old: T) => T)(old) : updater
}

export const isPageIndexHigherThanTotalPages = (pageIndex: number, totalPages: number) =>
  totalPages && totalPages > 0 && pageIndex + 1 > totalPages
