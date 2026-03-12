'use client'

import { useTranslations } from 'next-intl'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface RemoveGroupAssignmentModalProps {
  isOpen: boolean
  onOpenChange: (isOpen: boolean) => void
  groupName: string
  onConfirm: () => void
}

export const RemoveGroupAssignmentModal = (props: RemoveGroupAssignmentModalProps) => {
  const { isOpen, onOpenChange, groupName, onConfirm } = props
  const t = useTranslations('roles.groupAssignmentTab')

  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
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
