import { describe, expect, it } from 'vitest'

import { cn } from './utils'

describe('cn function', () => {
  it('should merge classes using clsx and twMerge', () => {
    const result = cn('text-white', 'bg-blue-500')

    expect(result).toBe('text-white bg-blue-500')
  })

  it('should handle conflicting classes correctly', () => {
    const result = cn('bg-red-500', 'bg-blue-500')

    expect(result).toBe('bg-blue-500')
  })

  it('should ignore undefined and null values', () => {
    const result = cn('bg-red-500', undefined, null, 'text-white')

    expect(result).toBe('bg-red-500 text-white')
  })

  it('should handle conditional classes', () => {
    const result = cn('bg-red-500', false && 'font-bold', 'text-white')

    expect(result).toBe('bg-red-500 text-white')
  })
})
