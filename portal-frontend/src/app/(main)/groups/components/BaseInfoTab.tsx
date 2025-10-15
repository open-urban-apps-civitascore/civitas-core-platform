'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { Select } from '@/components/form/fields/Select'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { FormFieldContainer } from '@/components/form/FormFieldContainer'
import { GroupData } from '@/types/groups'
import { useIsMobile } from '@/hooks/use-mobile'

interface BaseInfoTabProps {
  form: UseFormReturn<GroupData>
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { form } = props
  const t = useTranslations('groups')
  const isMobile = useIsMobile()

  const handleContactChange = (_value: string) => {
    form.setValue('contact', null)
  }

  return (
    <div>
      <FormFieldContainer className="pt-0 pb-3 text-xl">
        <h2>{t('details.baseInfo')}</h2>
      </FormFieldContainer>
      <FormFieldContainer>
        <TextField
          form={form}
          label={t('details.name')}
          name="title"
          placeholder={t('details.name')}
          formItemProps={{ className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]' }}
          required
        />
      </FormFieldContainer>

      <FormFieldContainer>
        <TextArea
          className="max-w-lg my-12"
          form={form}
          label={t('details.description')}
          name="description"
          placeholder={t('details.description')}
          formItemProps={{ className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]' }}
          required
        />
      </FormFieldContainer>
      <FormFieldContainer className="border-0">
        <Select
          form={form}
          id="contactSelect"
          label={t('details.contact')}
          name="contact"
          placeholder={t('details.contact')}
          options={[]}
          className={isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]'}
          onChange={handleContactChange}
          disabled
        />
      </FormFieldContainer>
    </div>
  )
}
