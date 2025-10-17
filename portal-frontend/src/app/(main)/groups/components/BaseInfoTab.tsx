'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2 } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { FieldErrors, useForm } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Select } from '@/components/form/fields/Select'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { Form } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import {
  CreateGroupData,
  Group,
  GroupBaseInfo,
  GroupBaseInfoSchema,
  GroupTabProps,
  UpdateGroupData,
} from '@/types/groups'

import { createGroup, updateGroup } from '../actions'

const getGroupBaseInfo = (groupData: Group): GroupBaseInfo => ({
  id: groupData.id,
  title: groupData.title,
  description: groupData.description,
  contact: groupData.contact ?? { id: '', displayName: '' },
})

const transformData = (formData: GroupBaseInfo) => {
  const parsed = GroupBaseInfoSchema.parse(formData)

  return {
    ...parsed,
    contact: parsed.contact?.id ? parsed.contact : null,
  }
}

interface BaseInfoTabProps extends GroupTabProps {
  isEditMode: boolean
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { groupData, isEditMode, onCancel } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const isMobile = useIsMobile()
  const router = useRouter()
  const availableContacts: { value: string; label: string }[] = []
  const [isLoading, setIsLoading] = useState(false)

  const form = useForm<GroupBaseInfo>({
    resolver: zodResolver(GroupBaseInfoSchema),
    defaultValues: { ...getGroupBaseInfo(groupData) },
  })

  useEffect(() => {
    form.reset(getGroupBaseInfo(groupData))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupData])

  const handleCreateGroup = async (formData: GroupBaseInfo) => {
    try {
      setIsLoading(true)
      const transformedGroupData = transformData(formData)
      // eslint-disable-next-line unused-imports/no-unused-vars
      const { id, ...groupData } = transformedGroupData
      const createGroupData: CreateGroupData = { ...groupData, parent: null, roles: [], subgroups: [], users: [] }
      const response = await createGroup(createGroupData)
      router.push(`/groups/${response.id}`)
    } catch (error) {
      console.error('An error occurred while creating the group: ', error)
    } finally {
      setIsLoading(false)
    }
  }

  const handleUpdateGroup = async (formData: GroupBaseInfo) => {
    try {
      setIsLoading(true)
      const transformedGroupData = transformData(formData)
      const updateGroupData: UpdateGroupData = {
        ...transformedGroupData,
        parent: groupData.parent,
        roles: groupData.roles,
        subgroups: groupData.subgroups,
        users: groupData.users,
      }
      await updateGroup(updateGroupData)
      router.refresh()
    } catch (error) {
      console.error('An error occurred while updating the group: ', error)
    } finally {
      setIsLoading(false)
    }
  }

  const handleSubmit = isEditMode ? handleUpdateGroup : handleCreateGroup
  const handleValidationErrors = (errors: FieldErrors<Group>) => console.error('Validation errors: ', errors)

  const handleContactChange = (_value: string) => {
    form.setValue('contact', { id: '', displayName: '' })
  }
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="h-8 w-8 animate-spin" />
        <span className="ml-2">{tCommon('loading')}</span>
      </div>
    )
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(handleSubmit, handleValidationErrors)}
        className="flex flex-col justify-between h-full"
      >
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-3 text-xl">
            <h2>{t('details.baseInfo')}</h2>
          </DetailsFieldContainer>
          <DetailsFieldContainer>
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
          </DetailsFieldContainer>

          <DetailsFieldContainer>
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
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-0">
            <Select
              form={form}
              id="contactSelect"
              label={t('details.contact')}
              name="contact"
              placeholder={t('details.contact')}
              options={availableContacts}
              className={isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]'}
              onChange={handleContactChange}
              disabled
            />
          </DetailsFieldContainer>
        </ContentCard>
        <ActionButtons
          onCancelClick={onCancel}
          confirmButtonType="submit"
          isConfirmButtonDisabled={!form.formState.isDirty}
        />
      </form>
    </Form>
  )
}
