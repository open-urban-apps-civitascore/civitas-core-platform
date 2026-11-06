'use client'

import { useCallback, useRef, useState } from 'react'

import type { DataSinkRemoval } from '@/types/dataSinkRemoval'
import { unconfirmedRemoval } from '@/utils/errors'

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

  /**
   * Sends the request. When the backend still refuses it as an unconfirmed removal, shows what it would remove and
   * sends it again confirmed. Resolves false when the user cancels; rethrows any other error.
   */
  const sendConfirmingRemoval = useCallback(
    async (send: (isDataLossConfirmed: boolean) => Promise<unknown>, isDataLossConfirmed: boolean) => {
      try {
        await send(isDataLossConfirmed)
        return true
      } catch (error) {
        const removalToConfirm = isDataLossConfirmed ? null : unconfirmedRemoval(error)
        if (!removalToConfirm) throw error
        const isConfirmed = await confirmRemoval(removalToConfirm)
        if (!isConfirmed) return false
        await send(true)
        return true
      }
    },
    [confirmRemoval],
  )

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

  return { confirmRemoval, sendConfirmingRemoval, dialog }
}
