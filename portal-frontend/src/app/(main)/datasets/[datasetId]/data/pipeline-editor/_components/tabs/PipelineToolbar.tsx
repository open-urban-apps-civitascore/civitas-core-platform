'use client'

/**
 * PipelineToolbar Component
 *
 * Toolbar for pipeline editor actions.
 * Provides Validate and Save functionality.
 * Validation results are now shown in the inspector panel.
 *
 */

import { CheckCircle2, Loader2, Save } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useCallback } from 'react'

import { Button } from '@/components/ui/button'

import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'
import { useActivePipeline } from '../../_hooks/use-active-pipeline'

// ============================================================================
// Props
// ============================================================================

interface PipelineToolbarProps {
  className?: string
}

// ============================================================================
// Component
// ============================================================================

/**
 * Toolbar with Validate and Save actions.
 * Save button is only enabled after validation passes.
 *
 */
export const PipelineToolbar: React.FC<PipelineToolbarProps> = ({ className = '' }) => {
  const t = useTranslations('datastructures.pipelineEditor')
  const { pipeline, runValidation, savePipeline, isDirty, canSave, isValidationRequired, isSaving } =
    useActivePipeline()

  /**
   * Handle validate button click.
   * Runs validation - results are shown in inspector panel.
   */
  const handleValidate = useCallback(() => {
    if (!pipeline) {
      return
    }

    runValidation()
  }, [pipeline, runValidation])

  /**
   * Handle save button click.
   * Only enabled when canSave is true (validation passed, no errors).
   */
  const handleSave = useCallback(() => {
    if (!pipeline || !canSave) {
      return
    }

    savePipeline()
  }, [pipeline, canSave, savePipeline])

  return (
    <div
      className={`flex items-center justify-between border-b border-border bg-muted/20 px-3 ${className}`}
      style={{ height: LAYOUT_DIMENSIONS.toolbarHeight }}
    >
      {/* Left side - Pipeline info */}
      <div className="flex items-center gap-2">
        {pipeline && (
          <>
            <span className="text-sm font-medium">{pipeline.name}</span>
            {isDirty && <span className="text-xs text-muted-foreground">{t('toolbar.unsavedChanges')}</span>}
          </>
        )}
      </div>

      {/* Center - Validation hint when needed */}
      <div className="flex items-center gap-2">
        {isDirty && isValidationRequired && (
          <span className="text-xs text-muted-foreground">{t('toolbar.validateBeforeSave')}</span>
        )}
      </div>

      {/* Right side - Actions */}
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={handleValidate} disabled={!pipeline || isSaving}>
          <CheckCircle2 className="mr-1 h-4 w-4" />
          {t('toolbar.validate')}
        </Button>

        <Button variant="default" size="sm" onClick={handleSave} disabled={!canSave || isSaving}>
          {isSaving ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Save className="mr-1 h-4 w-4" />}
          {isSaving ? t('toolbar.saving') : t('toolbar.save')}
        </Button>
      </div>
    </div>
  )
}
