'use client'

import { useCallback, useState } from 'react'

import { type ConfirmationDialog, useConfirmation } from '@/hooks/use-confirmation'
import type { DataSinkRemoval } from '@/types/dataSinkRemoval'
import { unconfirmedRemoval } from '@/utils/errors'

export interface RemovalConfirmationDialog extends ConfirmationDialog {
  removal: DataSinkRemoval | null
}

export const useRemovalConfirmation = () => {
  const { confirm, dialog: confirmation } = useConfirmation()
  const [removal, setRemoval] = useState<DataSinkRemoval | null>(null)

  /** Shows the removal and resolves with the user's answer. */
  const confirmRemoval = useCallback(
    async (removalToConfirm: DataSinkRemoval) => {
      setRemoval(removalToConfirm)
      const isConfirmed = await confirm()
      setRemoval(current => (current === removalToConfirm ? null : current))
      return isConfirmed
    },
    [confirm],
  )

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

  const dialog: RemovalConfirmationDialog = { ...confirmation, removal }

  return { confirmRemoval, sendConfirmingRemoval, dialog }
}
