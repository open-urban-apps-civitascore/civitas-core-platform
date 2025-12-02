'use client'

import { useReactFlow } from '@xyflow/react'
import { useCallback } from 'react'

import type { UMLDiagram } from '../types/diagram'
import { useUMLDiagramCore, type UseUMLDiagramCoreReturn } from './useUMLDiagramCore'

export interface UseUMLDiagramReturn extends UseUMLDiagramCoreReturn {
  // Layout operations that require ReactFlow instance
  autoLayout: () => void
  fitView: () => void
}

export const useUMLDiagram = (initialDiagram?: UMLDiagram): UseUMLDiagramReturn => {
  // Use the core hook for all diagram logic
  const coreHook = useUMLDiagramCore(initialDiagram)

  // Access ReactFlow instance for layout operations
  const reactFlowInstance = useReactFlow()

  // Layout operations that require ReactFlow
  const autoLayout = useCallback(() => {
    // Use ReactFlow's built-in layout or implement custom logic
    reactFlowInstance.fitView({ padding: 0.2 })
  }, [reactFlowInstance])

  const fitView = useCallback(() => {
    reactFlowInstance.fitView({ padding: 0.1 })
  }, [reactFlowInstance])

  return {
    ...coreHook,
    // Override layout operations with ReactFlow-enabled versions
    autoLayout,
    fitView,
  }
}
