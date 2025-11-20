'use client'

import { ReactFlowProvider } from '@xyflow/react'

import { TestNodes } from '../TestNodes'
import { UMLDiagramProvider } from '../UMLDiagramProvider'
import { UMLCanvas } from './UMLCanvas'

interface UMLCanvasProviderProps {
  className?: string
}

export const UMLCanvasProvider: React.FC<UMLCanvasProviderProps> = ({ className = '' }) => {
  return (
    <UMLDiagramProvider>
      <ReactFlowProvider>
        <UMLCanvas className={className} />
        <TestNodes />
      </ReactFlowProvider>
    </UMLDiagramProvider>
  )
}
