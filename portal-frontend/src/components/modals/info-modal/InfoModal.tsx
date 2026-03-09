'use client'

import { DialogProps } from '@radix-ui/react-dialog'
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

interface InfoModalProps extends DialogProps {
  title: string
  description: string
  buttonTitle?: string
  onClose: () => void
}

export const InfoModal = (props: InfoModalProps) => {
  const { title, description, open, buttonTitle, onOpenChange, onClose } = props
  const t = useTranslations('common.actions')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="infoModal" className="sm:max-w-md" showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter className="flex gap-2 sm:justify-end">
          <Button data-testid="closeButton" type="button" variant="default" onClick={onClose}>
            {buttonTitle || t('close')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
