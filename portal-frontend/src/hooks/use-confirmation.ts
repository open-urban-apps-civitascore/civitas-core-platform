'use client'

import { useCallback, useRef, useState } from 'react'

export interface ConfirmationDialog {
  open: boolean
  onConfirm: () => void
  onCancel: () => void
}

export const useConfirmation = () => {
  const [open, setOpen] = useState(false)
  const answerRef = useRef<((isConfirmed: boolean) => void) | null>(null)

  /** Opens the dialog and resolves with the user's answer. A new question cancels the open one. */
  const confirm = useCallback(() => {
    answerRef.current?.(false)
    setOpen(true)
    return new Promise<boolean>(resolve => {
      answerRef.current = resolve
    })
  }, [])

  const answer = useCallback((isConfirmed: boolean) => {
    answerRef.current?.(isConfirmed)
    answerRef.current = null
    setOpen(false)
  }, [])

  const dialog: ConfirmationDialog = {
    open,
    onConfirm: () => answer(true),
    onCancel: () => answer(false),
  }

  return { confirm, dialog }
}
