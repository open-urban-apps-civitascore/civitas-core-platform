import { act, renderHook } from '@testing-library/react'

import { useConfirmation } from './use-confirmation'

describe('useConfirmation', () => {
  it('resolves with the answer and closes the dialog', async () => {
    const { result } = renderHook(() => useConfirmation())

    let answer: Promise<boolean> | undefined
    act(() => {
      answer = result.current.confirm()
    })
    expect(result.current.dialog.open).toBe(true)

    act(() => result.current.dialog.onConfirm())

    await expect(answer).resolves.toBe(true)
    expect(result.current.dialog.open).toBe(false)
  })

  it('cancels the open question when a new one is asked', async () => {
    const { result } = renderHook(() => useConfirmation())

    let first: Promise<boolean> | undefined
    let second: Promise<boolean> | undefined
    act(() => {
      first = result.current.confirm()
    })
    act(() => {
      second = result.current.confirm()
    })
    act(() => result.current.dialog.onConfirm())

    await expect(first).resolves.toBe(false)
    await expect(second).resolves.toBe(true)
  })
})
