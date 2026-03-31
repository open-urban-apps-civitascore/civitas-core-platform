import { FormEvent, useEffect } from 'react'

import { useUnsavedChanges } from '@/contexts/unsaved-changes/UnsavedChangesContext'

export const useRegisterUnsavedChanges = (isDirty: boolean, saveHandler?: () => Promise<boolean>) => {
  const { setHasUnsavedChanges, setSaveHandler } = useUnsavedChanges()

  useEffect(() => {
    setHasUnsavedChanges(isDirty)
  }, [isDirty, setHasUnsavedChanges])

  useEffect(() => {
    setSaveHandler(saveHandler ?? null)
  }, [saveHandler, setSaveHandler])

  useEffect(() => {
    return () => {
      setHasUnsavedChanges(false)
      setSaveHandler(null)
    }
  }, [setHasUnsavedChanges, setSaveHandler])
}
