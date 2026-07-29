import { useCallback, useEffect, useRef } from 'react'

import { useUnsavedChanges } from '@/contexts/unsaved-changes/UnsavedChangesContext'

export const useRegisterUnsavedChanges = (
  isDirty: boolean,
  saveHandler?: () => Promise<boolean>,
  shouldRefreshAfterNavigate = false,
) => {
  const { setHasUnsavedChanges, setSaveHandler } = useUnsavedChanges()
  const saveHandlerRef = useRef<typeof saveHandler>(saveHandler)
  const hasSaveHandler = !!saveHandler

  useEffect(() => {
    saveHandlerRef.current = saveHandler
  }, [saveHandler])

  const stableSaveHandler = useCallback(() => {
    if (!saveHandlerRef.current) {
      return Promise.resolve(false)
    }

    return saveHandlerRef.current()
  }, [])

  useEffect(() => {
    setHasUnsavedChanges(isDirty)
  }, [isDirty, setHasUnsavedChanges])

  useEffect(() => {
    if (!hasSaveHandler) return
    setSaveHandler(stableSaveHandler, shouldRefreshAfterNavigate)
    return () => setSaveHandler(null)
  }, [hasSaveHandler, setSaveHandler, stableSaveHandler, shouldRefreshAfterNavigate])

  useEffect(() => {
    return () => {
      setHasUnsavedChanges(false)
    }
  }, [setHasUnsavedChanges])
}
