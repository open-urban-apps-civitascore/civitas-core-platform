'use client'

/**
 * PipelineEditorWrapper Component
 *
 * Client-side wrapper that provides ReactFlowProvider context.
 * Required because React Flow components need to be wrapped in ReactFlowProvider.
 *
 */

import { ReactFlowProvider } from '@xyflow/react'
import { useParams } from 'next/navigation'

import { useDatasetPermissionsById } from '@/hooks/use-dataset-permissions'

import { ReadOnlyProvider } from '../../_hooks/use-pipeline-read-only'
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
  const params = useParams<{ datasetId: string }>()

  const { canEditPipeline: canEdit, canCreatePipeline: canCreate } = useDatasetPermissionsById(params.datasetId)

  return (
    <ReactFlowProvider>
      <ReadOnlyProvider isReadOnly={!canEdit && !canCreate}>
        <PipelineEditorLayout className={className} />
      </ReadOnlyProvider>
    </ReactFlowProvider>
  )
}
