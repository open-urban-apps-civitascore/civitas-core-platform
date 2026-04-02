'use client'

import { DialogProps } from '@radix-ui/react-dialog'
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

interface ExitWarningModalProps extends DialogProps {
  title?: string
  description?: string
  isLoading?: boolean
  onDiscard: () => void
  onConfirm: () => void
}

export const ExitWarningModal = (props: ExitWarningModalProps) => {
  const { title, description, open, onOpenChange, onDiscard, onConfirm, isLoading = false } = props
  const t = useTranslations('common.exitModal')
  const tCommon = useTranslations('common')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="exitWarningModal" className="sm:max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{title || t('title')}</DialogTitle>
          <DialogDescription>{description || t('description')}</DialogDescription>
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
          <Button data-testid="saveButton" type="button" onClick={onConfirm} disabled={isLoading}>
            {tCommon('actions.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
