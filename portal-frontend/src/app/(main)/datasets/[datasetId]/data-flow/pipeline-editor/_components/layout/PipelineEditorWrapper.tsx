'use client'

/**
 * PipelineEditorWrapper Component
 *
 * Client-side wrapper that provides ReactFlowProvider context.
 * Required because React Flow components need to be wrapped in ReactFlowProvider.
 *
 */

import { ReactFlowProvider } from '@xyflow/react'
import { useTranslations } from 'next-intl'

import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'

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
  const { hasPermission } = usePermissions()
  const t = useTranslations('common')

  if (!hasPermission(PERMISSION_NAMES.DATASOURCE_READ)) {
    return (
      <div data-testid="noPermissionMessage" className="flex h-full items-center justify-center text-muted-foreground">
        {t('noPermission')}
      </div>
    )
  }

  return (
    <ReactFlowProvider>
      <PipelineEditorLayout className={className} />
    </ReactFlowProvider>
  )
}
