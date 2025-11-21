'use client'

import { ReactFlowProvider } from '@xyflow/react'

import type { DiagramSession } from '../../types/session'
import { UMLCanvas } from '../canvas/UMLCanvas'

interface TabContentProps {
  session: DiagramSession
  isActive: boolean
}

export const TabContent: React.FC<TabContentProps> = ({ session, isActive }) => {
  if (!isActive) {
    return null // Don't render inactive tabs to improve performance
  }

  return (
    <div className="flex-1 h-full">
      <ReactFlowProvider>
        <UMLCanvas className="flex-1" />
      </ReactFlowProvider>
    </div>
  )
}
