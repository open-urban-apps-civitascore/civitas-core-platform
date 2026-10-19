import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { useClampedText } from './use-clamped-text'

const CLIENT_HEIGHT = 40
const CHARS_PER_LINE = 10
const LINE_HEIGHT = 20

const createMockElement = () => {
  let text = ''
  return {
    clientHeight: CLIENT_HEIGHT,
    get scrollHeight() {
      return Math.ceil(text.length / CHARS_PER_LINE) * LINE_HEIGHT
    },
    get textContent() {
      return text
    },
    set textContent(value: string) {
      text = value
    },
  } as unknown as HTMLElement
}

describe('useClampedText', () => {
  it('returns the full text when it already fits', () => {
    const ref = { current: createMockElement() }
    const text = 'short text'

    const { result } = renderHook(() => useClampedText(text, ref))

    expect(result.current).toBe('short text')
  })

  it('truncates with an ellipsis to the longest substring that fits when the text overflows', () => {
    const ref = { current: createMockElement() }
    const text = '123456789012345678901234567890'

    const { result } = renderHook(() => useClampedText(text, ref))

    expect(result.current).toBe(`${text.slice(0, 19)}…`)
  })

  it('does not throw and keeps the full text when the ref is null', () => {
    const ref = { current: null }
    const text = 'some text'

    const { result } = renderHook(() => useClampedText(text, ref))

    expect(result.current).toBe('some text')
  })

  it('re-measures when the text changes', () => {
    const ref = { current: createMockElement() }

    const { result, rerender } = renderHook(({ text }) => useClampedText(text, ref), {
      initialProps: { text: 'short' },
    })

    expect(result.current).toBe('short')

    rerender({ text: '123456789012345678901234567890' })

    expect(result.current).toBe(`${'123456789012345678901234567890'.slice(0, 19)}…`)
  })
})
