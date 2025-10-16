'use client'

import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { Select } from '@/components/form/fields/Select'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { FormFieldContainer } from '@/components/form/FormFieldContainer'
import { Form } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { useQueryParams } from '@/hooks/useQueryParams'
import { GroupBaseInfo, GroupBaseInfoSchema, GroupData } from '@/types/groups'
import { mapApiGroupBaseInfoData } from '@/utils/groups'
import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { createGroup, updateGroup } from '../actions'

interface BaseInfoTabProps {
  groupData: GroupData
  isEditMode: boolean
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { groupData, isEditMode } = props
  const t = useTranslations('groups')
  const isMobile = useIsMobile()
  const router = useRouter()
  const { setApiRequestParams } = useQueryParams()
  const groupBaseInfo = {
    id: groupData.id,
    title: groupData.title,
    description: groupData.description,
    contact: groupData.contact,
  }

  const goToGroupsList = () => {
    const apiParams = setApiRequestParams()
    router.push(`/groups?${apiParams}`)
  }

  const form = useForm<GroupBaseInfo>({
    resolver: zodResolver(GroupBaseInfoSchema),
    defaultValues: { ...groupBaseInfo },
  })

  const handleCreateGroup = async (formData: GroupBaseInfo) => {
    const parsed = GroupBaseInfoSchema.parse(formData)
    const groupData = mapApiGroupBaseInfoData(parsed)

    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = groupData
    const response = await createGroup(createGroupData)
    console.log('response: ', response)
    // router.push
  }

  const handleUpdateGroup = async (formData: GroupBaseInfo) => {
    console.log('handleUpdateGroup')
    const parsed = GroupBaseInfoSchema.parse(formData)
    const groupData = mapApiGroupBaseInfoData(parsed)
    const response = await updateGroup(groupData)
    console.log('response: ', response)
    router.refresh()
  }

  const handleSubmit = isEditMode ? handleUpdateGroup : handleCreateGroup

  const handleContactChange = (_value: string) => {
    form.setValue('contact', null)
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="flex flex-col justify-between h-full">
        <ContentCard>
          <FormFieldContainer className="pt-0 pb-3 text-xl">
            <h2>{t('details.baseInfo')}</h2>
          </FormFieldContainer>
          <FormFieldContainer>
            <TextField
              form={form}
              label={t('details.name')}
              name="title"
              placeholder={t('details.name')}
              formItemProps={{
                className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
              }}
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
              formItemProps={{
                className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
              }}
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
        </ContentCard>
        <ActionButtons onCancelClick={goToGroupsList} confirmButtonType="submit" />
      </form>
    </Form>
  )
}
