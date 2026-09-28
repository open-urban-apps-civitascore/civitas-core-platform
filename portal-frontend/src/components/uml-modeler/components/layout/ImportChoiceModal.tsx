'use client'

import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface ImportChoiceModalProps {
  isOpen: boolean
  onCancel: () => void
  onAdd: () => void
  onReplace: () => void
}

/**
 * Asks what an imported file does to a diagram that has classes already: replace them, or add the
 * file's classes beside them. Adding is what loading a standard structure does, so both imports end
 * in the same diagram for the same document.
 */
export const ImportChoiceModal: React.FC<ImportChoiceModalProps> = ({ isOpen, onCancel, onAdd, onReplace }) => {
  const t = useTranslations('umlModeler')
  const tActions = useTranslations('common.actions')

  return (
    <Dialog open={isOpen} onOpenChange={open => !open && onCancel()}>
      <DialogContent data-testid="importChoiceModal" className="sm:max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{t('import.overwriteModal.title')}</DialogTitle>
          <DialogDescription asChild>
            <div>{t('import.overwriteModal.description')}</div>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="flex gap-2 sm:justify-end">
          <Button data-testid="discardButton" type="button" variant="outline" onClick={onCancel}>
            {tActions('cancel')}
          </Button>
          <Button data-testid="addButton" type="button" variant="outline" onClick={onAdd}>
            {t('import.overwriteModal.add')}
          </Button>
          <Button
            data-testid="confirmButton"
            type="button"
            onClick={onReplace}
            className="text-white bg-destructive border-destructive hover:bg-destructive/70"
          >
            {t('import.overwriteModal.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
