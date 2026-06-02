import { isPageIndexHigherThanTotalPages, resolveUpdater } from './table'

describe('resolveUpdater', () => {
  it('calls the updater function with the old value when updater is a function', () => {
    const updater = (old: number) => old + 1
    const result = resolveUpdater(updater, 5)
    expect(result).toBe(6)
  })

  it('returns the updater directly when updater is a value', () => {
    const result = resolveUpdater(42, 0)
    expect(result).toBe(42)
  })
})

describe('isPageIndexHigherThanTotalPages', () => {
  it('returns true when pageIndex exceeds totalPages', () => {
    const result = isPageIndexHigherThanTotalPages(5, 3)
    expect(result).toBe(true)
  })

  it('returns falsy when totalPages is higher than pageIndex', () => {
    const result = isPageIndexHigherThanTotalPages(1, 2)
    expect(result).toBeFalsy()
  })

  it('returns falsy when totalPages is 0', () => {
    const result = isPageIndexHigherThanTotalPages(0, 0)
    expect(result).toBeFalsy()
  })

  it('returns falsy when totalPages is undefined', () => {
    const result = isPageIndexHigherThanTotalPages(0, undefined as unknown as number)
    expect(result).toBeFalsy()
  })
})
