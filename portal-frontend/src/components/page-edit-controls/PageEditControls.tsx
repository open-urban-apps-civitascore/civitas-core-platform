'use client'

import { useTranslations } from 'next-intl'
import { JSX } from 'react'

import { cn } from '@/lib/utils'
import { DatasetStatusTypes } from '@/types/datasets'
import { DatasourceStatusType } from '@/types/datasources'

import { ActionButtons, ActionButtonsProps } from '../action-buttons/ActionButtons'
import { StatusDropdown } from '../status-dropdown/StatusDropdown'
import { Button } from '../ui/button'

type PageEditControlsProps<T extends DatasourceStatusType | DatasetStatusTypes> = ActionButtonsProps & {
  status: T
  onStatusChange: (status: T) => void
  canSetAvailable: boolean
  onEditClick: () => void
  isReadOnly: boolean
  formId?: string
  statusOptions: T[]
}

const PageEditControls = <T extends DatasourceStatusType | DatasetStatusTypes>(
  props: PageEditControlsProps<T>,
): JSX.Element => {
  const {
    status,
    onStatusChange,
    canSetAvailable,
    hasCard = true,
    wrapperClassname,
    className,
    onEditClick,
    isReadOnly = true,
    formId,
    statusOptions,
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
      <StatusDropdown
        status={status}
        statusOptions={statusOptions}
        onStatusChange={onStatusChange}
        canSetAvailable={canSetAvailable}
        isReadOnly={isReadOnly}
      />

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
          <Button
            data-testid="editButton"
            type="button"
            onClick={onEditClick}
            className={cn('py-0', hasCard && 'py-3')}
          >
            {t('actions.edit')}
          </Button>
        )}
      </div>
    </div>
  )
}

export default PageEditControls
