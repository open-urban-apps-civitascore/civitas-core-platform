'use client'

import type { ReactNode } from 'react'

import { UMLDiagramContextProvider } from '../hooks/UMLDiagramContext'
import { useUMLDiagramCore } from '../hooks/useUMLDiagramCore'
import type { UMLDiagram } from '../types/diagram'

interface UMLDiagramProviderProps {
  children: ReactNode
  initialDiagram?: UMLDiagram
}

export const UMLDiagramProvider: React.FC<UMLDiagramProviderProps> = ({ children, initialDiagram }) => {
  // Create the shared state instance
  const diagramState = useUMLDiagramCore(initialDiagram)

  return <UMLDiagramContextProvider value={diagramState}>{children}</UMLDiagramContextProvider>
}
