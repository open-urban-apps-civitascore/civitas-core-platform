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
  hasCard?: boolean
  confirmButtonTitle?: string
}

export const ActionButtons = (props: ActionButtonsProps) => {
  const {
    confirmButtonTitle,
    confirmButtonType,
    onCancelClick,
    isConfirmButtonDisabled = false,
    isCancelButtonDisabled = false,
    className,
    hasCard = true,
  } = props
  const t = useTranslations('common')

  const Buttons = (
    <>
      <Button
        data-testid="cancelButton"
        type="reset"
        variant="secondary"
        onClick={onCancelClick}
        disabled={isCancelButtonDisabled}
      >
        {t('actions.cancel')}
      </Button>
      <Button
        data-testid="confirmButton"
        type={confirmButtonType}
        onClick={confirmButtonType === 'button' ? props.onConfirmClick : undefined}
        disabled={isConfirmButtonDisabled}
      >
        {confirmButtonTitle || t('actions.submit')}
      </Button>
    </>
  )
  return (
    <div className={cn('flex w-full justify-end', className)}>
      {hasCard ? (
        <ContentCard className="flex gap-4 p-3">{Buttons}</ContentCard>
      ) : (
        <div className="flex gap-4 py-3">{Buttons}</div>
      )}
    </div>
  )
}
