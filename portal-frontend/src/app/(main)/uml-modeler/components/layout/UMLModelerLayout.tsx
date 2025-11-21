'use client'

import { ReactFlowProvider } from '@xyflow/react'

import { UMLCanvas } from '../canvas/UMLCanvas'
import { PropertyInspector } from '../inspector/PropertyInspector'
import { ElementPalette } from '../palette/ElementPalette'
import { UMLDiagramProvider } from '../UMLDiagramProvider'

interface UMLModelerLayoutProps {
  className?: string
}

export const UMLModelerLayout: React.FC<UMLModelerLayoutProps> = ({ className = '' }) => {
  return (
    <div className={`h-full flex bg-gray-100 ${className}`}>
      <UMLDiagramProvider>
        <ReactFlowProvider>
          {/* Element Palette - Left Sidebar */}
          <ElementPalette className="flex-shrink-0" />

          {/* Main Canvas Area */}
          <div className="flex-1 flex flex-col min-w-0">
            <UMLCanvas className="flex-1" />
          </div>

          {/* Property Inspector - Right Sidebar */}
          <PropertyInspector className="flex-shrink-0" />
        </ReactFlowProvider>
      </UMLDiagramProvider>
    </div>
  )
}
