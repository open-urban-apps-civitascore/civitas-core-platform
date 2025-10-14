'use client'

import { useTranslations } from 'next-intl'
import { useForm, UseFormReturn } from 'react-hook-form'

import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { GroupData, GroupSchema } from '@/types/groups'
import { zodResolver } from '@hookform/resolvers/zod'
import { Form } from '@/components/ui/form'

const defaultGroup: GroupData = {
  id: '',
  title: '',
  description: '',
  roles: [],
  users: [],
  contact: '',
  subgroups: [],
}

interface BaseInfoTabProps {
  form: UseFormReturn<GroupData>
  //   groupData: GroupData
  onSubmit: (groupData: GroupData) => void
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { form, onSubmit } = props
  const t = useTranslations('groups')

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <div className="grid gap-4 space-y-8 mb-4 max-w-3xl grid-cols-1">
          <TextField
            form={form}
            label={t('details.name')}
            name="title"
            placeholder={t('details.name')}
            formItemProps={{ className: 'grid grid-cols-[200px_300px]' }}
          />

          <TextArea
            className="max-w-lg my-12"
            form={form}
            label={t('details.description')}
            name="description"
            placeholder={t('details.description')}
            formItemProps={{ className: 'grid grid-cols-[200px_300px]' }}
          />
        </div>
      </form>
    </Form>
  )
}
