'use client'

import { useCallback, useRef, useState } from 'react'

import type { DataSinkRemovalImpact } from '@/types/removalImpact'
import { removalImpactToConfirm } from '@/utils/errors'

export interface RemovalConfirmationDialog {
  open: boolean
  impact: DataSinkRemovalImpact | null
  isLoading: boolean
  onConfirm: () => void
  onCancel: () => void
}

interface RunConfirmedOptions {
  /** Asked before the first attempt; the dialog stays open while the removal runs. */
  impact?: DataSinkRemovalImpact
  /** Ids the user has already confirmed. */
  confirmedRemovalIds?: string[]
}

const union = (first: string[], second: string[]) => [...new Set([...first, ...second])]

export const useRemovalConfirmation = () => {
  const [impact, setImpact] = useState<DataSinkRemovalImpact | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const answerRef = useRef<((isConfirmed: boolean) => void) | null>(null)

  const ask = useCallback((impactToConfirm: DataSinkRemovalImpact) => {
    setImpact(impactToConfirm)
    return new Promise<boolean>(resolve => {
      answerRef.current = resolve
    })
  }, [])

  const answer = useCallback((isConfirmed: boolean) => {
    answerRef.current?.(isConfirmed)
    answerRef.current = null
  }, [])

  const close = useCallback(() => {
    setImpact(null)
    setIsLoading(false)
  }, [])

  /** Shows the impact and resolves with the user's answer. */
  const confirmRemoval = useCallback(
    async (impactToConfirm: DataSinkRemovalImpact): Promise<boolean> => {
      try {
        return await ask(impactToConfirm)
      } finally {
        close()
      }
    },
    [ask, close],
  )

  /**
   * Runs `attempt` with the confirmed ids. When the backend refuses because the impact changed, shows the new
   * impact and retries after a confirmation. Resolves false when the user cancels; rethrows any other error.
   */
  const runConfirmed = useCallback(
    async (
      attempt: (confirmedRemovalIds: string[]) => Promise<unknown>,
      options: RunConfirmedOptions = {},
    ): Promise<boolean> => {
      let impactToConfirm = options.impact ?? null
      let confirmedIds = options.confirmedRemovalIds ?? []
      try {
        for (;;) {
          if (impactToConfirm) {
            const isConfirmed = await ask(impactToConfirm)
            if (!isConfirmed) return false
            confirmedIds = union(confirmedIds, impactToConfirm.confirmationIds)
            setIsLoading(true)
          }
          try {
            await attempt(confirmedIds)
            return true
          } catch (error) {
            impactToConfirm = removalImpactToConfirm(error)
            if (!impactToConfirm) throw error
          } finally {
            setIsLoading(false)
          }
        }
      } finally {
        close()
      }
    },
    [ask, close],
  )

  const dialog: RemovalConfirmationDialog = {
    open: impact !== null,
    impact,
    isLoading,
    onConfirm: () => answer(true),
    onCancel: () => answer(false),
  }

  return { confirmRemoval, runConfirmed, dialog }
}
