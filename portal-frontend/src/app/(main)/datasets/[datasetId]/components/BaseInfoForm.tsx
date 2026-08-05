import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { usePermissions } from '@/hooks/use-permissions'
import { cn } from '@/lib/utils'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { SelectOption } from '@/types/common'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { DatasetFormDraft } from '@/types/datasets'

import { ChangeDatapoolModal } from './ChangeDatapoolModal'

interface BaseInfoFormProps {
  form: UseFormReturn<DatasetFormDraft>
  isReadOnly: boolean
  isLoading: boolean
  datapoolOptions: SelectOption[]
}
export const BaseInfoForm = (props: BaseInfoFormProps) => {
  const { form, isReadOnly, isLoading, datapoolOptions } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')

  const { hasPermission, hasScopedPermission } = usePermissions()
  const canReadDatapools = hasPermission(PERMISSION_NAMES.DATAPOOL_READ)

  const [isWarningModalOpen, setIsWarningModalOpen] = useState(false)
  const [pendingDatapoolId, setPendingDatapoolId] = useState<string | null>(null)

  const nameValue = form.watch('name')
  const datapoolId = form.watch('datapoolId')

  const isAssignedDatapoolAnonymous =
    !!datapoolId && !hasScopedPermission(PERMISSION_NAMES.DATAPOOL_READ, ASSIGNMENT_SCOPE_TYPES.DATAPOOL, datapoolId)

  const permissionGuardedDatapoolOptions = isAssignedDatapoolAnonymous
    ? [{ value: datapoolId, label: t('anonymousDatapool') }, ...datapoolOptions.filter(o => o.value !== datapoolId)]
    : datapoolOptions

  return (
    <>
      <div className={cn('max-w-300 flex gap-2 pt-2')}>
        {nameValue ? (
          <CircleCheckBig className="w-6 h-6 text-green-600" />
        ) : (
          <CircleDashed className="w-6 h-6 text-muted-foreground" />
        )}
        <div className={cn('w-full flex flex-col')}>
          <DetailsFieldContainer className="pt-0">
            <SubHeader title={t('overview.info.title')} titleClassName="text-xl leading-none font-semibold" />
          </DetailsFieldContainer>
          {isLoading ? (
            <LoadingSpinner className="h-[364px]" />
          ) : (
            <>
              <DetailsFieldContainer className="max-w-300">
                <TextField
                  id="datasetTitle"
                  form={form}
                  label={t('form.name')}
                  name="name"
                  placeholder={t('form.name')}
                  disabled={isReadOnly}
                  required
                />
              </DetailsFieldContainer>
              <DetailsFieldContainer className="max-w-300">
                <FormSelect
                  id="datasetDatapool"
                  placeholder={canReadDatapools ? t('form.datapoolSelectPlaceholder') : tCommon('info.notAvailable')}
                  label={t('form.datapoolSelect')}
                  options={permissionGuardedDatapoolOptions}
                  form={form}
                  name="datapoolId"
                  disabled={isReadOnly || !canReadDatapools}
                  data-testid="datapoolSelect"
                  onChange={value => {
                    setPendingDatapoolId(value === 'none' ? null : value)
                    setIsWarningModalOpen(true)
                  }}
                  hasPlaceholderWhenDisabled
                />
              </DetailsFieldContainer>
              <DetailsFieldContainer className="max-w-300">
                <FormTextArea
                  id="datasetDescription"
                  form={form}
                  label={t('form.description')}
                  name="description"
                  placeholder={t('form.description')}
                  hint={t('form.descriptionHint')}
                  maxLength={150}
                  hasCharacterCount
                  required
                  className="min-h-[100px] resize-none"
                  disabled={isReadOnly}
                />
              </DetailsFieldContainer>
            </>
          )}
        </div>
      </div>

      <ChangeDatapoolModal
        isOpen={isWarningModalOpen}
        onDiscard={() => {
          setIsWarningModalOpen(false)
          setPendingDatapoolId(null)
        }}
        onConfirm={() => {
          form.setValue('datapoolId', pendingDatapoolId, { shouldDirty: true })
          setIsWarningModalOpen(false)
          setPendingDatapoolId(null)
        }}
      />
    </>
  )
}
