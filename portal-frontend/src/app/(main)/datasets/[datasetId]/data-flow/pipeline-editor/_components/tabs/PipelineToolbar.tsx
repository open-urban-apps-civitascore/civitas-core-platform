'use client'

/**
 * PipelineToolbar Component
 *
 * Toolbar for pipeline editor actions.
 * Provides Validate and pipeline settings (gear icon with delete) functionality.
 * Validation results are shown in the inspector panel.
 *
 */

import { CheckCircle2, EllipsisVertical, Trash2 } from 'lucide-react'
import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useState } from 'react'

import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useDatasetPermissionsById } from '@/hooks/use-dataset-permissions'

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
 * Toolbar with pipeline name, gear menu, and Validate action.
 * Gear icon opens a dropdown with "Delete Pipeline" option.
 *
 */
export const PipelineToolbar: React.FC<PipelineToolbarProps> = ({ className = '' }) => {
  const t = useTranslations('pipelineEditor')
  const { pipeline, runValidation, deletePipeline, isDirty, isDeleting } = useActivePipeline()
  const { datasetId } = useParams<{ datasetId: string }>()
  const { canDeletePipeline: canDelete, canEditPipeline: canEdit } = useDatasetPermissionsById(datasetId)

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
              {canDelete && (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm" className="h-7 w-7 p-0" title={t('toolbar.pipelineSettings')}>
                      <EllipsisVertical className="h-4 w-4 text-muted-foreground" />
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
              )}
            </>
          )}
        </div>

        {/* Right side - Validate */}
        <div className="flex items-center gap-2">
          {canEdit && (
            <Button variant="outline" size="sm" onClick={handleValidate} disabled={!pipeline}>
              <CheckCircle2 className="mr-1 h-4 w-4" />
              {t('toolbar.validate')}
            </Button>
          )}
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
