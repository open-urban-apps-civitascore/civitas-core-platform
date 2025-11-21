'use client'

import { ReactFlowProvider } from '@xyflow/react'

import { TestEdges } from '../TestEdges'
// import { TestNodes } from '../TestNodes'
import { UMLDiagramProvider } from '../UMLDiagramProvider'
import { UMLCanvas } from './UMLCanvas'

interface UMLCanvasProviderProps {
  className?: string
}

export const UMLCanvasProvider: React.FC<UMLCanvasProviderProps> = ({ className = '' }) => {
  return (
    <UMLDiagramProvider>
      <ReactFlowProvider>
        <TestEdges />
        <UMLCanvas className={className} />

        {/* <TestNodes /> */}
      </ReactFlowProvider>
    </UMLDiagramProvider>
  )
}
