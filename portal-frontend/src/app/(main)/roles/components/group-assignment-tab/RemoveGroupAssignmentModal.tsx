'use client'

import { useTranslations } from 'next-intl'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface RemoveGroupAssignmentModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  groupName: string
  onConfirm: () => void
}

export const RemoveGroupAssignmentModal = (props: RemoveGroupAssignmentModalProps) => {
  const { open, onOpenChange, groupName, onConfirm } = props
  const t = useTranslations('roles.groupAssignmentTab')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>{t('removeModal.title')}</DialogTitle>
          <DialogDescription>{t('removeModal.description', { groupName })}</DialogDescription>
        </DialogHeader>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={() => {
            onConfirm()
            onOpenChange(false)
          }}
          onCancelClick={() => onOpenChange(false)}
          confirmButtonTitle={t('removeModal.confirm')}
          hasCard={false}
        />
      </DialogContent>
    </Dialog>
  )
}
