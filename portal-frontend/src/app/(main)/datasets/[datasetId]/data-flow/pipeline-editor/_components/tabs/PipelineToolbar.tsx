'use client'

/**
 * PipelineToolbar Component
 *
 * Toolbar for pipeline editor actions.
 * Provides Validate, Save, and pipeline settings (gear icon with delete) functionality.
 * Validation results are now shown in the inspector panel.
 *
 */

import { CheckCircle2, Loader2, Save, Settings, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useCallback, useState } from 'react'

import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'

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
 * Toolbar with pipeline name, gear menu, Validate, and Save actions.
 * Save button is only enabled after validation passes.
 * Gear icon opens a dropdown with "Delete Pipeline" option.
 *
 */
export const PipelineToolbar: React.FC<PipelineToolbarProps> = ({ className = '' }) => {
  const t = useTranslations('datastructures.pipelineEditor')
  const {
    pipeline,
    runValidation,
    savePipeline,
    deletePipeline,
    isDirty,
    canSave,
    isValidationRequired,
    isSaving,
    isDeleting,
  } = useActivePipeline()

  const [shouldShowDeleteConfirm, setShouldShowDeleteConfirm] = useState(false)

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

  /**
   * Handle delete confirmation.
   */
  const handleDeleteConfirm = useCallback(() => {
    setShouldShowDeleteConfirm(false)
    deletePipeline()
  }, [deletePipeline])

  return (
    <>
      <div
        className={`flex items-center justify-between border-b border-border bg-muted/20 px-3 ${className}`}
        style={{ height: LAYOUT_DIMENSIONS.toolbarHeight }}
      >
        {/* Left side - Pipeline info + gear icon */}
        <div className="flex items-center gap-2">
          {pipeline && (
            <>
              <span className="text-sm font-medium">{pipeline.name}</span>
              {isDirty && <span className="text-xs text-muted-foreground">{t('toolbar.unsavedChanges')}</span>}

              {/* Gear icon with dropdown menu */}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="sm" className="h-7 w-7 p-0" title={t('toolbar.pipelineSettings')}>
                    <Settings className="h-4 w-4 text-muted-foreground" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  <DropdownMenuItem
                    onClick={() => setShouldShowDeleteConfirm(true)}
                    className="text-destructive focus:text-destructive"
                    disabled={isDeleting}
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    {t('toolbar.deletePipeline')}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
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

      {/* Delete confirmation modal */}
      <WarningModal
        open={shouldShowDeleteConfirm}
        onOpenChange={setShouldShowDeleteConfirm}
        title={t('toolbar.deleteConfirmTitle')}
        description={t('toolbar.deleteConfirmDescription')}
        confirmButtonTitle={t('toolbar.deletePipeline')}
        onConfirm={handleDeleteConfirm}
        onDiscard={() => setShouldShowDeleteConfirm(false)}
        isLoading={isDeleting}
      />
    </>
  )
}
