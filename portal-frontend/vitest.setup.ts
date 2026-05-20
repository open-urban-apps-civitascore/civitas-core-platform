import '@testing-library/jest-dom'

import { vi } from 'vitest'

// Object.groupBy was added in Node.js 21; polyfill for Node 20 test environments
if (!('groupBy' in Object)) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ;(Object as any).groupBy = function <T, K extends PropertyKey>(
    iterable: Iterable<T>,
    keyFn: (item: T, index: number) => K,
  ): Record<K, T[]> {
    const result = Object.create(null) as Record<K, T[]>
    let index = 0
    for (const item of iterable) {
      const key = keyFn(item, index++)
      if (!Object.prototype.hasOwnProperty.call(result, key)) {
        result[key] = []
      }
      result[key].push(item)
    }
    return result
  }
}

process.env.NEXT_PUBLIC_JSON_SERVER_HOST = 'http://localhost'
process.env.NEXT_PUBLIC_JSON_SERVER_PORT = '3001'

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: false,
    requestNavigation: vi.fn(),
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

vi.mock('@/hooks/use-register-unsaved-changes', () => ({
  useRegisterUnsavedChanges: vi.fn(),
}))
