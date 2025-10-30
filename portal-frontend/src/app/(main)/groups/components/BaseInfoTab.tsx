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
import { AutoComplete, SelectItem } from '@/components/form/fields/AutoComplete'
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
import { UserResponse } from '@/types/users'

import { createGroup, updateGroup } from '../actions'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

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
  const { groupData, isEditMode } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const isMobile = useIsMobile()
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const [isContactListOpen, setIsContactListOpen] = useState(false)
  const [contacts, setContacts] = useState<SelectItem[]>([])
  const [searchString, setSearchString] = useState('')

  const form = useForm<GroupBaseInfo>({
    resolver: zodResolver(GroupBaseInfoSchema),
    defaultValues: { ...getGroupBaseInfo(groupData) },
  })

  useEffect(() => {
    form.reset(getGroupBaseInfo(groupData))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupData])

  useEffect(() => {
    console.log(contacts)
  }, [contacts])

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

  const handleSelectContact = (newSelection: SelectItem) => {
    form.setValue('contact', { id: newSelection.value, displayName: newSelection.label })
  }

  const getContacts = async (searchString: string) => {
    try {
      const queryParam = `displayName_like=${searchString}`
      const usersResponse = await fetch(`${URL}/users?${queryParam}`)
      if (!usersResponse.ok) {
        throw new Error('Error fetching contacts data')
      }

      const contactsData: UserResponse[] = await usersResponse.json()
      const contacts = contactsData.map(contact => ({ value: contact.id, label: contact.displayName }))

      return contacts
    } catch (error) {
      console.error('Error fetching contacts data:', error)
      throw new Error('Error fetching contacts data')
    } finally {
      setIsLoading(false)
    }
  }

  const handleContactInputChange = async (value: string) => {
    if (value.length >= 3) {
      setSearchString(value)
      const contacts = await getContacts(value)
      setContacts(contacts)
      setIsContactListOpen(true)
    }
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
            <AutoComplete
              id="contact"
              isOpen={isContactListOpen}
              listItems={contacts}
              form={form}
              name="contact"
              placeholder={t('details.contact')}
              label={t('details.contact')}
              required={true}
              onOpenChange={setIsContactListOpen}
              onInputChange={handleContactInputChange}
              onSelectItem={handleSelectContact}
              input={searchString}
            />
          </DetailsFieldContainer>
        </ContentCard>
        <ActionButtons
          onCancelClick={() => form.reset()}
          confirmButtonType="submit"
          isConfirmButtonDisabled={!form.formState.isDirty}
          isCancelButtonDisabled={!form.formState.isDirty}
        />
      </form>
    </Form>
  )
}
