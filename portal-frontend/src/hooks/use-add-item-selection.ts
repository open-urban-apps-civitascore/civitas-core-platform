import { RowSelectionState } from '@tanstack/react-table'
import { useEffect, useMemo, useRef, useState } from 'react'

interface UseAddItemSelectionOptions<T extends { id: string }> {
  assignedIds: string[]
  items: T[]
  open: boolean
}

export const useAddItemSelection = <T extends { id: string }>(options: UseAddItemSelectionOptions<T>) => {
  const { assignedIds, items, open } = options

  const [selection, setSelection] = useState<RowSelectionState>({})
  const [newlySelectedCount, setNewlySelectedCount] = useState(0)
  const selectedItemsRef = useRef<Map<string, T>>(new Map())
  const prevSelectionRef = useRef<RowSelectionState>({})

  const assignedIdsSet = useMemo(() => new Set(assignedIds), [assignedIds])

  useEffect(() => {
    if (!open) {
      setSelection({})
      setNewlySelectedCount(0)
      selectedItemsRef.current.clear()
      prevSelectionRef.current = {}
    }
  }, [open])

  const handleSelectionChange = (updater: RowSelectionState | ((old: RowSelectionState) => RowSelectionState)) => {
    const prev = prevSelectionRef.current
    const raw = typeof updater === 'function' ? updater(prev) : updater

    // Filter out already-assigned IDs
    const filtered: RowSelectionState = {}
    for (const id of Object.keys(raw)) {
      if (!assignedIdsSet.has(id)) {
        filtered[id] = true
      }
    }

    // Sync the cross-page ref: add newly selected, remove deselected on current page
    const currentItemIds = new Set(items.map(i => i.id))
    const addedIds = Object.keys(filtered).filter(id => !prev[id])
    // Only treat as removed if the ID belongs to the current page — IDs from other pages
    // disappear from TanStack's selection on page change but should stay in the ref
    const removedIds = Object.keys(prev).filter(id => !filtered[id] && currentItemIds.has(id))

    for (const id of addedIds) {
      const item = items.find(i => i.id === id)
      if (item) selectedItemsRef.current.set(id, item)
    }
    for (const id of removedIds) {
      selectedItemsRef.current.delete(id)
    }

    prevSelectionRef.current = filtered
    setSelection(filtered)
    setNewlySelectedCount(selectedItemsRef.current.size)
  }

  return {
    selection,
    selectedItemsRef,
    assignedIdsSet,
    handleSelectionChange,
    newlySelectedCount,
  }
}
