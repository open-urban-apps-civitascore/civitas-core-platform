'use client'

/**
 * Pipeline editor read-only context.
 *
 * Mirrors the UML modeler's read-only pattern: the editable/read-only state is
 * derived once at the top (from permissions) and provided to the whole editor,
 * so canvas, palette and inspector consume a single flag instead of each
 * resolving permissions on their own.
 */

import { createContext, type ReactNode, useContext } from 'react'

interface ReadOnlyContextValue {
  isReadOnly: boolean
}

const ReadOnlyContext = createContext<ReadOnlyContextValue | null>(null)

interface ReadOnlyProviderProps {
  children: ReactNode
  isReadOnly: boolean
}

export const ReadOnlyProvider = ({ children, isReadOnly }: ReadOnlyProviderProps) => (
  <ReadOnlyContext.Provider value={{ isReadOnly }}>{children}</ReadOnlyContext.Provider>
)

export const useReadOnly = (): ReadOnlyContextValue => {
  const context = useContext(ReadOnlyContext)
  if (!context) {
    throw new Error('useReadOnly must be used within a ReadOnlyProvider')
  }
  return context
}
