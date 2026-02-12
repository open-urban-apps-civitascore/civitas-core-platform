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

export type ActionButtonsProps = (FormButtonProps | ConfirmButtons) & {
  onCancelClick: () => void
  isCancelButtonDisabled?: boolean
  isConfirmButtonDisabled?: boolean
  hasCard?: boolean
  confirmButtonTitle?: string
  cancelButtonTitle?: string
  wrapperClassname?: string
}

export const ActionButtons = (props: ActionButtonsProps) => {
  const {
    confirmButtonTitle,
    cancelButtonTitle,
    confirmButtonType,
    onCancelClick,
    isConfirmButtonDisabled = false,
    isCancelButtonDisabled = false,
    className,
    wrapperClassname,
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
        {cancelButtonTitle || t('actions.cancel')}
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
    <div className={cn('flex w-full justify-end', wrapperClassname)}>
      {hasCard ? (
        <ContentCard className={cn('flex gap-4 py-3', className)}>{Buttons}</ContentCard>
      ) : (
        <div className={cn('flex gap-4 py-3', className)}>{Buttons}</div>
      )}
    </div>
  )
}
