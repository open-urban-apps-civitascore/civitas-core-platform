'use client'
import '@xyflow/react/dist/style.css'

import { UMLCanvasProvider } from './components/canvas/UMLCanvasProvider'

const UmlModelerPage = () => {
  return (
    <div className="flex h-full w-full flex-1 flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">UML Modeler</h1>
          <p className="text-sm text-muted-foreground">Create and edit UML class diagrams with professional tools</p>
        </div>
        <div className="flex gap-2">
          {/* Placeholder for toolbar buttons - will be implemented in future phases */}
          <div className="text-xs text-muted-foreground">Phase 1: Foundation ✓ | Phase 2: Visual UML Nodes ✓</div>
        </div>
      </div>

      <div className="h-full w-full rounded-xl border bg-background overflow-hidden">
        <UMLCanvasProvider className="rounded-xl" />
      </div>
    </div>
  )
}

export default UmlModelerPage
