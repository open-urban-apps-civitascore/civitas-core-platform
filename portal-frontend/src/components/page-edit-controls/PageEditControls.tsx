'use client'

import { useTranslations } from 'next-intl'
import { JSX } from 'react'

import { cn } from '@/lib/utils'
import { StatusTypes } from '@/types/common'

import { ActionButtons, ActionButtonsProps } from '../action-buttons/ActionButtons'
import { StatusDropdown } from '../status-dropdown/StatusDropdown'
import { Button } from '../ui/button'

type PageEditControlsProps<T extends StatusTypes> = ActionButtonsProps & {
  onEditClick: () => void
  isReadOnly: boolean
  formId?: string
  canEdit?: boolean
  statusProps?: {
    status: T
    onStatusChange: (status: T) => void
    canStage: boolean
    canSetDraft?: boolean
    canRelease?: boolean
    statusOptions: T[]
    statusHint?: string
    availableHint?: string
  }
}

const PageEditControls = <T extends StatusTypes>(props: PageEditControlsProps<T>): JSX.Element => {
  const {
    hasCard = true,
    wrapperClassname,
    className,
    onEditClick,
    isReadOnly = true,
    formId,
    canEdit = true,
    statusProps,
    ...actionButtonsProps
  } = props

  const t = useTranslations('common')

  return (
    <div
      className={cn(
        hasCard ? 'flex w-full justify-end' : 'flex items-center gap-4 px-[var(--layout-padding)]',
        wrapperClassname,
      )}
    >
      {statusProps && <StatusDropdown {...statusProps} isReadOnly={isReadOnly} />}

      <div>
        {!isReadOnly ? (
          <ActionButtons
            {...actionButtonsProps}
            formId={formId}
            hasCard={false}
            wrapperClassname="w-auto"
            className={cn('py-0', hasCard && 'py-3', className)}
          />
        ) : (
          canEdit && (
            <Button
              data-testid="editButton"
              type="button"
              onClick={onEditClick}
              className={cn('py-0', hasCard && 'py-3')}
            >
              {t('actions.edit')}
            </Button>
          )
        )}
      </div>
    </div>
  )
}

export default PageEditControls
