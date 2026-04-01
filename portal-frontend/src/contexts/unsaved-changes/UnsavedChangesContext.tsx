'use client'

import { createContext, useContext } from 'react'

export interface UnsavedChangesContextValue {
  hasUnsavedChanges: boolean
  setHasUnsavedChanges: (dirty: boolean) => void
  setSaveHandler: (handler: (() => Promise<boolean>) | null) => void
  requestNavigation: (href: string) => void
  requestBack: () => void
}

export const UnsavedChangesContext = createContext<UnsavedChangesContextValue | null>(null)

export const useUnsavedChanges = () => {
  const context = useContext(UnsavedChangesContext)
  if (!context) {
    throw new Error('useUnsavedChanges must be used within an UnsavedChangesProvider')
  }
  return context
}
