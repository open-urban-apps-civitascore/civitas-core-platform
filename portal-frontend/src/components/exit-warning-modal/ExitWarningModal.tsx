'use client'

import { Trash2 } from 'lucide-react'
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

interface ExitWarningModalProps {
  isOpen: boolean
  onClose: () => void
  onDiscard: () => void
  onSave: () => void
  isLoading?: boolean
}

export const ExitWarningModal = (props: ExitWarningModalProps) => {
  const { isOpen, onClose, onDiscard, onSave, isLoading = false } = props
  const t = useTranslations('datasources.exitModal')
  const tCommon = useTranslations('common')

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent data-testid="exitWarningModal" className="sm:max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>
        <DialogFooter className="flex gap-2 sm:justify-end">
          <Button
            data-testid="discardButton"
            type="button"
            variant="outline"
            onClick={onDiscard}
            disabled={isLoading}
            className="text-destructive border-destructive hover:bg-destructive/10 hover:text-destructive"
          >
            <Trash2 className="w-4 h-4 mr-1" />
            {t('discard')}
          </Button>
          <Button data-testid="saveButton" type="button" onClick={onSave} disabled={isLoading}>
            {tCommon('actions.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
