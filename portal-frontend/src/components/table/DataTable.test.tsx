import { getAriaSort } from './DataTable'

describe('getAriaSort', () => {
  it('returns the correct value', () => {
    expect(getAriaSort('asc')).toEqual('ascending')
    expect(getAriaSort('desc')).toEqual('descending')
    expect(getAriaSort(false)).toEqual('none')
  })
})
