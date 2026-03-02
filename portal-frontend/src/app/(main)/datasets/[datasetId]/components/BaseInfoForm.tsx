import { Circle, CircleCheckBig } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { cn } from '@/lib/utils'
import { DatasetFormDraft } from '@/types/datasets'

interface BaseInfoFormProps {
  form: UseFormReturn<DatasetFormDraft>
  isReadOnly: boolean
  isLoading: boolean
}
export const BaseInfoForm = (props: BaseInfoFormProps) => {
  const { form, isReadOnly, isLoading } = props
  const t = useTranslations('datasets')

  const dataset = form.getValues()

  return (
    <div className={cn('max-w-300 flex gap-2 pt-2')}>
      {dataset.name ? <CircleCheckBig /> : <Circle />}
      <div className={cn('w-full flex flex-col')}>
        <DetailsFieldContainer className="pt-0">
          <SubHeader title={t('overview.info.title')} titleClassName="text-2xl leading-none font-bold" />
        </DetailsFieldContainer>
        {isLoading ? (
          <LoadingSpinner className="h-[364px]" />
        ) : (
          <>
            <DetailsFieldContainer className="max-w-300">
              <TextField
                id="datasetTitle"
                form={form}
                label={t('overview.info.name')}
                name="name"
                placeholder={t('overview.info.name')}
                disabled={isReadOnly}
                required
              />
            </DetailsFieldContainer>

            <DetailsFieldContainer className="max-w-300">
              <FormTextArea
                id="datasetDescription"
                form={form}
                label={t('overview.info.description')}
                name="description"
                placeholder={t('overview.info.description')}
                disabled={isReadOnly}
                hint={t('overview.info.descriptionHint')}
                maxLength={150}
                hasCharacterCount
                required
                className="min-h-[100px] resize-none"
              />
            </DetailsFieldContainer>
          </>
        )}
      </div>
    </div>
  )
}
