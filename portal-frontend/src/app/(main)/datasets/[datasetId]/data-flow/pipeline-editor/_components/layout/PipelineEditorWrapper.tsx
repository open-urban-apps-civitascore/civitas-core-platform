'use client'

/**
 * PipelineEditorWrapper Component
 *
 * Client-side wrapper that provides ReactFlowProvider context.
 * Required because React Flow components need to be wrapped in ReactFlowProvider.
 *
 */

import { ReactFlowProvider } from '@xyflow/react'

import { PipelineEditorLayout } from './PipelineEditorLayout'

// ============================================================================
// Props
// ============================================================================

interface PipelineEditorWrapperProps {
  className?: string
}

// ============================================================================
// Component
// ============================================================================

/**
 * Wrapper component that provides ReactFlowProvider context.
 *
 */
export const PipelineEditorWrapper: React.FC<PipelineEditorWrapperProps> = ({ className = '' }) => {
  return (
    <ReactFlowProvider>
      <PipelineEditorLayout className={className} />
    </ReactFlowProvider>
  )
}
