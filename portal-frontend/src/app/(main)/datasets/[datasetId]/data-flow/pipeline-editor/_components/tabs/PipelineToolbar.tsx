'use client'

/**
 * PipelineToolbar Component
 *
 * Toolbar for pipeline editor actions.
 * Provides Validate and pipeline settings (gear icon with delete) functionality.
 * Validation results are shown in the inspector panel.
 *
 */

import { CheckCircle2, EllipsisVertical, Pencil, Trash2 } from 'lucide-react'
import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useRef, useState } from 'react'

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
 * Gear icon opens a dropdown with "Rename" and "Delete Pipeline" options.
 *
 */
export const PipelineToolbar: React.FC<PipelineToolbarProps> = ({ className = '' }) => {
  const t = useTranslations('pipelineEditor')
  const { pipeline, runValidation, deletePipeline, renamePipeline, isDirty, isDeleting, activeSessionId } =
    useActivePipeline()
  const { datasetId } = useParams<{ datasetId: string }>()
  const { canDeletePipeline: canDelete, canEditPipeline: canEdit } = useDatasetPermissionsById(datasetId)

  const [shouldShowDeleteConfirm, setShouldShowDeleteConfirm] = useState(false)
  const [isEditingName, setIsEditingName] = useState(false)
  const [editName, setEditName] = useState('')
  const pendingRenameRef = useRef(false)

  const isUnsaved = !pipeline?.id
  const canDiscard = canDelete || isUnsaved
  const canShowMenu = canDiscard || canEdit
  const pipelineRemovalLabel = isUnsaved ? t('toolbar.discardPipeline') : t('toolbar.deletePipeline')
  const pipelineRemovalConfirmTitle = isUnsaved ? t('toolbar.discardConfirmTitle') : t('toolbar.deleteConfirmTitle')
  const pipelineRemovalConfirmDescription = isUnsaved
    ? t('toolbar.discardConfirmDescription')
    : t('toolbar.deleteConfirmDescription')

  // Switching tabs while renaming would otherwise leave the input open, editing the wrong pipeline.
  useEffect(() => {
    setIsEditingName(false)
  }, [activeSessionId])

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

  const handleRenameSelect = useCallback(() => {
    pendingRenameRef.current = true
  }, [])

  // Rename mode starts only after the menu has closed, so the menu does not move the focus back to its button
  const handleMenuCloseAutoFocus = useCallback(
    (e: Event) => {
      if (!pendingRenameRef.current) return
      pendingRenameRef.current = false
      e.preventDefault()
      if (!pipeline) return
      setEditName(pipeline.name)
      setIsEditingName(true)
    },
    [pipeline],
  )

  /**
   * Commit the edited name and leave rename mode.
   */
  const commitRename = useCallback(() => {
    renamePipeline(editName)
    setIsEditingName(false)
  }, [renamePipeline, editName])

  const handleRenameKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter') {
        commitRename()
      } else if (e.key === 'Escape') {
        setIsEditingName(false)
      }
    },
    [commitRename],
  )

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
              {isEditingName ? (
                <input
                  autoFocus
                  type="text"
                  value={editName}
                  onChange={e => setEditName(e.target.value)}
                  onKeyDown={handleRenameKeyDown}
                  onBlur={commitRename}
                  aria-label={t('toolbar.renamePipeline')}
                  className="border-none bg-transparent text-sm font-semibold outline-none"
                />
              ) : (
                <span className="text-sm font-semibold">{pipeline.name}</span>
              )}
              {isDirty && <span className="text-xs text-muted-foreground">{t('toolbar.unsavedChanges')}</span>}

              {/* Gear icon with dropdown menu */}
              {canShowMenu && (
                // Non-modal: a modal menu's focus trap fights the rename input we hand focus to
                // once the menu finishes closing (see onCloseAutoFocus below) — its MutationObserver
                // watches for the closing content's own DOM removal and yanks focus back onto it.
                <DropdownMenu modal={false}>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm" className="h-7 w-7 p-0" title={t('toolbar.pipelineSettings')}>
                      <EllipsisVertical className="h-4 w-4 text-muted-foreground" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start" onCloseAutoFocus={handleMenuCloseAutoFocus}>
                    {canEdit && (
                      <DropdownMenuItem onSelect={handleRenameSelect}>
                        <Pencil className="mr-2 h-4 w-4" />
                        {t('toolbar.renamePipeline')}
                      </DropdownMenuItem>
                    )}
                    {canDiscard && (
                      <DropdownMenuItem
                        onClick={() => setShouldShowDeleteConfirm(true)}
                        className="text-destructive focus:text-destructive"
                        disabled={isDeleting}
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        {pipelineRemovalLabel}
                      </DropdownMenuItem>
                    )}
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
        title={pipelineRemovalConfirmTitle}
        description={pipelineRemovalConfirmDescription}
        confirmButtonTitle={pipelineRemovalLabel}
        onConfirm={handleDeleteConfirm}
        onDiscard={() => setShouldShowDeleteConfirm(false)}
        isLoading={isDeleting}
      />
    </>
  )
}
