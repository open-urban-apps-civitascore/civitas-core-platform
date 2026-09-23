import { renderHook } from '@testing-library/react'
import { act } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useIsTruncated } from './use-is-truncated'

let resizeCallback: () => void
const observeMock = vi.fn()
const disconnectMock = vi.fn()

vi.stubGlobal(
  'ResizeObserver',
  vi.fn(
    class {
      observe = observeMock
      disconnect = disconnectMock
      unobserve = vi.fn()
      constructor(callback: () => void) {
        resizeCallback = callback
      }
    },
  ),
)

const createMockElement = (scrollWidth: number, clientWidth: number) => ({ scrollWidth, clientWidth }) as HTMLElement

describe('useIsTruncated', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should observe the element', () => {
    const element = createMockElement(100, 200)
    const ref = { current: element }

    renderHook(() => useIsTruncated(ref))

    expect(observeMock).toHaveBeenCalledWith(element)
  })

  it('should return false when element is not truncated', () => {
    const element = createMockElement(100, 200)
    const ref = { current: element }

    const { result } = renderHook(() => useIsTruncated(ref))

    act(() => resizeCallback())

    expect(result.current).toBe(false)
  })

  it('should return true when element is truncated', () => {
    const element = createMockElement(300, 200)
    const ref = { current: element }

    const { result } = renderHook(() => useIsTruncated(ref))

    act(() => resizeCallback())

    expect(result.current).toBe(true)
  })

  it('should disconnect observer on unmount', () => {
    const element = createMockElement(100, 200)
    const ref = { current: element }

    const { unmount } = renderHook(() => useIsTruncated(ref))
    unmount()

    expect(disconnectMock).toHaveBeenCalled()
  })

  it('should not observe when ref is null', () => {
    const ref = { current: null }

    renderHook(() => useIsTruncated(ref))

    expect(observeMock).not.toHaveBeenCalled()
  })

  it('should update when element size changes', () => {
    const element = createMockElement(100, 200)
    const ref = { current: element }

    const { result } = renderHook(() => useIsTruncated(ref))

    act(() => resizeCallback())
    expect(result.current).toBe(false)

    Object.defineProperty(element, 'scrollWidth', { value: 300, configurable: true })
    act(() => resizeCallback())
    expect(result.current).toBe(true)
  })
})
