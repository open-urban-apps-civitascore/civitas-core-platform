'use client'

/**
 * useActivePipeline Hook
 *
 * Context hook for accessing the active pipeline state and operations.
 * Must be used within a PipelineEditorProvider.
 *
 */

import { createContext, useContext } from 'react'

import type { ActivePipelineContextValue } from '../_types/context'

// ============================================================================
// Context Definition
// ============================================================================

/**
 * Context for the active pipeline.
 * Default value is null - must be used within PipelineEditorProvider.
 */
export const ActivePipelineContext = createContext<ActivePipelineContextValue | null>(null)

// ============================================================================
// Context Provider Component (for use in PipelineEditorProvider)
// ============================================================================

/**
 * Provider component wrapper for type safety.
 *
 */
export const ActivePipelineProvider = ActivePipelineContext.Provider

// ============================================================================
// Hook
// ============================================================================

/**
 * Hook to access the active pipeline context.
 *
 * @returns ActivePipelineContextValue
 * @throws Error if used outside of PipelineEditorProvider
 *
 */
export const useActivePipeline = (): ActivePipelineContextValue => {
  const context = useContext(ActivePipelineContext)

  if (!context) {
    throw new Error('useActivePipeline must be used within a PipelineEditorProvider')
  }

  return context
}
