'use client'

import { useCallback, useRef, useState } from 'react'

import type { DataSinkRemoval } from '@/utils/dataSinkRemoval'

export interface RemovalConfirmationDialog {
  open: boolean
  removal: DataSinkRemoval | null
  onConfirm: () => void
  onCancel: () => void
}

export const useRemovalConfirmation = () => {
  const [removal, setRemoval] = useState<DataSinkRemoval | null>(null)
  const answerRef = useRef<((isConfirmed: boolean) => void) | null>(null)

  /** Shows the removal and resolves with the user's answer. */
  const confirmRemoval = useCallback((removalToConfirm: DataSinkRemoval) => {
    setRemoval(removalToConfirm)
    return new Promise<boolean>(resolve => {
      answerRef.current = resolve
    })
  }, [])

  const answer = useCallback((isConfirmed: boolean) => {
    answerRef.current?.(isConfirmed)
    answerRef.current = null
    setRemoval(null)
  }, [])

  const dialog: RemovalConfirmationDialog = {
    open: removal !== null,
    removal,
    onConfirm: () => answer(true),
    onCancel: () => answer(false),
  }

  return { confirmRemoval, dialog }
}
