'use client'

import { createContext, useContext } from 'react'

import type { UseUMLDiagramCoreReturn } from './useUMLDiagramCore'

// Context for sharing UML diagram state between components
const UMLDiagramContext = createContext<UseUMLDiagramCoreReturn | null>(null)

// Hook to consume the shared UML diagram state
export const useUMLDiagram = (): UseUMLDiagramCoreReturn => {
  const context = useContext(UMLDiagramContext)

  if (!context) {
    throw new Error('useUMLDiagram must be used within a UMLDiagramProvider')
  }

  return context
}

// Export the context provider for direct use
export const UMLDiagramContextProvider = UMLDiagramContext.Provider
