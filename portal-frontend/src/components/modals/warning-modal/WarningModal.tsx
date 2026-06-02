'use client'

import { DialogProps } from '@radix-ui/react-dialog'
import { useTranslations } from 'next-intl'
import { ReactNode } from 'react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface WarningModalProps extends DialogProps {
  title: string
  description: ReactNode
  isLoading?: boolean
  confirmButtonTitle?: string
  onDiscard: () => void
  onConfirm?: () => void
}

export const WarningModal = (props: WarningModalProps) => {
  const { title, description, open, confirmButtonTitle, onOpenChange, onDiscard, onConfirm, isLoading = false } = props
  const t = useTranslations('common.actions')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="exitWarningModal" className="sm:max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription asChild>
            <div>{description}</div>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="flex gap-2 sm:justify-end">
          <Button data-testid="discardButton" type="button" variant="outline" onClick={onDiscard} disabled={isLoading}>
            {t('cancel')}
          </Button>
          {onConfirm && (
            <Button
              data-testid="confirmButton"
              type="button"
              onClick={onConfirm}
              disabled={isLoading}
              className="text-white bg-destructive  border-destructive hover:bg-destructive/70"
            >
              {confirmButtonTitle || t('submit')}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
