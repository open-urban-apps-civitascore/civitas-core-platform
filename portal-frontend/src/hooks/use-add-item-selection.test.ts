import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { useAddItemSelection } from './use-add-item-selection'

type TestItem = { id: string; name: string }

const makeItems = (count: number, startId = 1): TestItem[] =>
  Array.from({ length: count }, (_, i) => ({
    id: String(startId + i),
    name: `Item ${startId + i}`,
  }))

describe('useAddItemSelection', () => {
  it('returns empty selection initially', () => {
    const { result } = renderHook(() => useAddItemSelection({ assignedIds: [], items: makeItems(3), open: true }))

    expect(result.current.selection).toEqual({})
    expect(result.current.newlySelectedCount).toBe(0)
    expect(result.current.assignedIdsSet.size).toBe(0)
  })

  it('tracks selected items in the ref', () => {
    const items = makeItems(3)
    const { result } = renderHook(() => useAddItemSelection({ assignedIds: [], items, open: true }))

    act(() => {
      result.current.handleSelectionChange({ '1': true, '2': true })
    })

    expect(result.current.selection).toEqual({ '1': true, '2': true })
    expect(result.current.newlySelectedCount).toBe(2)
    expect(result.current.selectedItemsRef.current.get('1')).toEqual(items[0])
    expect(result.current.selectedItemsRef.current.get('2')).toEqual(items[1])
  })

  it('removes deselected items from ref', () => {
    const items = makeItems(3)
    const { result } = renderHook(() => useAddItemSelection({ assignedIds: [], items, open: true }))

    act(() => {
      result.current.handleSelectionChange({ '1': true, '2': true })
    })

    act(() => {
      result.current.handleSelectionChange({ '2': true })
    })

    expect(result.current.selection).toEqual({ '2': true })
    expect(result.current.newlySelectedCount).toBe(1)
    expect(result.current.selectedItemsRef.current.has('1')).toBe(false)
    expect(result.current.selectedItemsRef.current.has('2')).toBe(true)
  })

  it('filters assigned IDs out of selection state', () => {
    const items = makeItems(3)
    const { result } = renderHook(() => useAddItemSelection({ assignedIds: ['1'], items, open: true }))

    act(() => {
      result.current.handleSelectionChange({ '1': true, '2': true })
    })

    // Assigned ID '1' should be filtered out of selection
    expect(result.current.selection).toEqual({ '2': true })
    // Only newly selected item should be in ref
    expect(result.current.newlySelectedCount).toBe(1)
    expect(result.current.selectedItemsRef.current.has('1')).toBe(false)
    expect(result.current.selectedItemsRef.current.has('2')).toBe(true)
  })

  it('provides assignedIdsSet for O(1) lookup', () => {
    const { result } = renderHook(() =>
      useAddItemSelection({ assignedIds: ['1', '3'], items: makeItems(3), open: true }),
    )

    expect(result.current.assignedIdsSet.has('1')).toBe(true)
    expect(result.current.assignedIdsSet.has('3')).toBe(true)
    expect(result.current.assignedIdsSet.has('2')).toBe(false)
  })

  it('preserves selections across page changes (different items arrays)', () => {
    const page1Items = makeItems(5, 1)
    const page2Items = makeItems(5, 6)

    const { result, rerender } = renderHook(
      ({ items }: { items: TestItem[] }) => useAddItemSelection({ assignedIds: [], items, open: true }),
      { initialProps: { items: page1Items } },
    )

    // Select items on page 1
    act(() => {
      result.current.handleSelectionChange({ '2': true, '4': true })
    })

    expect(result.current.newlySelectedCount).toBe(2)
    expect(result.current.selectedItemsRef.current.get('2')).toEqual(page1Items[1])
    expect(result.current.selectedItemsRef.current.get('4')).toEqual(page1Items[3])

    // Simulate page change — new items, but selection ref persists
    rerender({ items: page2Items })

    // Ref still has page 1 selections
    expect(result.current.newlySelectedCount).toBe(2)
    expect(result.current.selectedItemsRef.current.get('2')).toEqual(page1Items[1])
    expect(result.current.selectedItemsRef.current.get('4')).toEqual(page1Items[3])

    // Select items on page 2
    act(() => {
      result.current.handleSelectionChange({ '7': true })
    })

    // Now has 3 total: 2 from page 1 + 1 from page 2
    expect(result.current.newlySelectedCount).toBe(3)
    expect(result.current.selectedItemsRef.current.get('2')).toEqual(page1Items[1])
    expect(result.current.selectedItemsRef.current.get('4')).toEqual(page1Items[3])
    expect(result.current.selectedItemsRef.current.get('7')).toEqual(page2Items[1])
  })

  it('resets selection and ref when dialog closes', () => {
    const items = makeItems(3)
    const { result, rerender } = renderHook(
      ({ open }: { open: boolean }) => useAddItemSelection({ assignedIds: [], items, open }),
      { initialProps: { open: true } },
    )

    act(() => {
      result.current.handleSelectionChange({ '1': true, '2': true })
    })

    expect(result.current.newlySelectedCount).toBe(2)

    // Close dialog
    rerender({ open: false })

    expect(result.current.selection).toEqual({})
    expect(result.current.newlySelectedCount).toBe(0)
    expect(result.current.selectedItemsRef.current.size).toBe(0)
  })

  it('handles updater function form of handleSelectionChange', () => {
    const items = makeItems(3)
    const { result } = renderHook(() => useAddItemSelection({ assignedIds: [], items, open: true }))

    act(() => {
      result.current.handleSelectionChange(() => ({ '1': true }))
    })

    expect(result.current.selection).toEqual({ '1': true })
    expect(result.current.newlySelectedCount).toBe(1)
  })

  it('allows deselecting an item after navigating away and back', () => {
    const page1Items = makeItems(5, 1)
    const page2Items = makeItems(5, 6)

    const { result, rerender } = renderHook(
      ({ items }: { items: TestItem[] }) => useAddItemSelection({ assignedIds: [], items, open: true }),
      { initialProps: { items: page1Items } },
    )

    // Select two items on page 1
    act(() => {
      result.current.handleSelectionChange({ '2': true, '4': true })
    })
    expect(result.current.newlySelectedCount).toBe(2)

    // Navigate to page 2
    rerender({ items: page2Items })

    // Navigate back to page 1
    rerender({ items: page1Items })

    // Deselect item '2' — only '4' should remain
    act(() => {
      result.current.handleSelectionChange({ '4': true })
    })

    expect(result.current.newlySelectedCount).toBe(1)
    expect(result.current.selectedItemsRef.current.has('2')).toBe(false)
    expect(result.current.selectedItemsRef.current.has('4')).toBe(true)
  })

  it('resets cleanly when dialog is re-opened after close', () => {
    const items = makeItems(3)
    const { result, rerender } = renderHook(
      ({ open }: { open: boolean }) => useAddItemSelection({ assignedIds: [], items, open }),
      { initialProps: { open: true } },
    )

    // Select items
    act(() => {
      result.current.handleSelectionChange({ '1': true, '2': true })
    })
    expect(result.current.newlySelectedCount).toBe(2)

    // Close dialog
    rerender({ open: false })
    expect(result.current.newlySelectedCount).toBe(0)
    expect(result.current.selection).toEqual({})

    // Re-open dialog — should start fresh
    rerender({ open: true })
    expect(result.current.newlySelectedCount).toBe(0)
    expect(result.current.selection).toEqual({})
    expect(result.current.selectedItemsRef.current.size).toBe(0)
  })

  it('handles select-all scenario with assigned items', () => {
    const items = makeItems(5)
    const { result } = renderHook(() => useAddItemSelection({ assignedIds: ['1', '3'], items, open: true }))

    // Simulate select-all: all 5 IDs become true
    act(() => {
      result.current.handleSelectionChange({
        '1': true,
        '2': true,
        '3': true,
        '4': true,
        '5': true,
      })
    })

    // Assigned IDs should be filtered from selection state
    expect(result.current.selection).toEqual({ '2': true, '4': true, '5': true })
    // Only non-assigned items tracked in ref
    expect(result.current.newlySelectedCount).toBe(3)
    expect(result.current.selectedItemsRef.current.has('1')).toBe(false)
    expect(result.current.selectedItemsRef.current.has('3')).toBe(false)
  })
})
