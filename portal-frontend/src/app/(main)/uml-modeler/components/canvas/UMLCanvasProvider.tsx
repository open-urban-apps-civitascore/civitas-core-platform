'use client'

import { ReactFlowProvider } from '@xyflow/react'

import { UMLCanvas } from './UMLCanvas'

interface UMLCanvasProviderProps {
  className?: string
}

export const UMLCanvasProvider: React.FC<UMLCanvasProviderProps> = ({ className = '' }) => {
  return (
    <ReactFlowProvider>
      <UMLCanvas className={className} />
    </ReactFlowProvider>
  )
}
