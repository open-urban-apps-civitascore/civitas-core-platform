'use client'

import { useTranslations } from 'next-intl'
import React, { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

import { ContentCard } from '../content-card/ContentCard'
import { Button } from '../ui/button'

interface FormButtonProps extends HTMLAttributes<HTMLDivElement> {
  confirmButtonType: 'submit'
}

interface ConfirmButtons extends HTMLAttributes<HTMLDivElement> {
  onConfirmClick: () => void
  confirmButtonType: 'button'
}

type ActionButtonsProps = (FormButtonProps | ConfirmButtons) & {
  onCancelClick: () => void
  isCancelButtonDisabled?: boolean
  isConfirmButtonDisabled?: boolean
}

export const ActionButtons = (props: ActionButtonsProps) => {
  const {
    confirmButtonType,
    onCancelClick,
    isConfirmButtonDisabled = false,
    isCancelButtonDisabled = false,
    className,
  } = props
  const t = useTranslations('common')
  return (
    <div className={cn('flex w-full justify-end', className)}>
      <ContentCard className="flex gap-4 p-3">
        <Button type="reset" variant="secondary" onClick={onCancelClick} disabled={isCancelButtonDisabled}>
          {t('actions.cancel')}
        </Button>
        <Button
          type={confirmButtonType}
          onClick={confirmButtonType === 'button' ? props.onConfirmClick : undefined}
          disabled={isConfirmButtonDisabled}
        >
          {t('actions.submit')}
        </Button>
      </ContentCard>
    </div>
  )
}
