'use client'

import { ReactFlowProvider } from '@xyflow/react'

import type { UMLDiagram } from '../../types/diagram'
import type { DiagramSession } from '../../types/session'
import { UMLCanvas } from '../canvas/UMLCanvas'
import { UMLDiagramProvider } from '../UMLDiagramProvider'

interface TabContentProps {
  session: DiagramSession
  isActive: boolean
  onSessionUpdate: (sessionId: string, diagram: UMLDiagram) => void
}

export const TabContent: React.FC<TabContentProps> = ({ session, isActive, onSessionUpdate }) => {
  if (!isActive) {
    return null // Don't render inactive tabs to improve performance
  }

  return (
    <div className="flex-1 h-full">
      <UMLDiagramProvider
        key={session.id}
        initialDiagram={session.diagram}
        onChange={diagram => onSessionUpdate(session.id, diagram)}
      >
        <ReactFlowProvider>
          <UMLCanvas className="flex-1" />
        </ReactFlowProvider>
      </UMLDiagramProvider>
    </div>
  )
}
