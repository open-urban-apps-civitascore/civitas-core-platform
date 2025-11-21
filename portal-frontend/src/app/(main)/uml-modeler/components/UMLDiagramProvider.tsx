'use client'

import { type ReactNode, useEffect, useRef } from 'react'

import { UMLDiagramContextProvider } from '../hooks/UMLDiagramContext'
import { useUMLDiagramCore } from '../hooks/useUMLDiagramCore'
import type { UMLDiagram } from '../types/diagram'

interface UMLDiagramProviderProps {
  children: ReactNode
  initialDiagram?: UMLDiagram
  onChange?: (diagram: UMLDiagram) => void
}

export const UMLDiagramProvider: React.FC<UMLDiagramProviderProps> = ({ children, initialDiagram, onChange }) => {
  // Create the shared state instance
  const diagramState = useUMLDiagramCore(initialDiagram)
  const previousDiagramRef = useRef(diagramState.diagram)
  useEffect(() => {
    // Only call onChange if the diagram actually changed
    if (onChange && diagramState.diagram !== previousDiagramRef.current) {
      // Check if there are actual changes (not just reference changes)
      const hasRealChanges =
        diagramState.diagram.nodes !== previousDiagramRef.current.nodes ||
        diagramState.diagram.edges !== previousDiagramRef.current.edges ||
        diagramState.diagram.isDirty !== previousDiagramRef.current.isDirty

      if (hasRealChanges) {
        onChange(diagramState.diagram)
        previousDiagramRef.current = diagramState.diagram
      }
    }
  }, [diagramState.diagram, onChange])

  return <UMLDiagramContextProvider value={diagramState}>{children}</UMLDiagramContextProvider>
}
