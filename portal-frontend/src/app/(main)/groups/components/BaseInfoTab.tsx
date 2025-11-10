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
import { useDebounce } from '@/hooks/useDebounce'
import { cn } from '@/lib/utils'
import {
  CreateGroupData,
  Group,
  GroupBaseFormData,
  GroupBaseFormDataSchema,
  GroupTabProps,
  UpdateGroupData,
} from '@/types/groups'
import { UserResponse } from '@/types/users'
import { mapGroupToBaseFormData } from '@/utils/groups'

import { createGroup, updateGroup } from '../actions'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`
const MIN_LENGTH = 2

const getContactListItems = (contacts: Contact[]) =>
  contacts.map(contact => ({
    value: contact.id,
    label: (
      <div>
        <p>{contact.displayName}</p>
        <p className="font-xs opacity-60">{contact.email}</p>
      </div>
    ),
  }))

type Contact = {
  id: string
  displayName: string
  email: string
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
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null)
  const [contacts, setContacts] = useState<Contact[]>([])
  const [contactListItems, setContactListItems] = useState<SelectItem[]>([])
  const [contactInput, setContactInput] = useState('')
  const debouncedInput = useDebounce(contactInput, 300)

  const form = useForm<GroupBaseFormData>({
    resolver: zodResolver(GroupBaseFormDataSchema),
    defaultValues: { ...mapGroupToBaseFormData(groupData) },
  })

  const resetAutocomplete = (contact: Contact | null) => {
    setContacts(contact ? [contact] : [])
    setContactListItems(getContactListItems(contact ? [contact] : []))
    setContactInput(contact?.displayName || '')
  }

  useEffect(() => {
    form.setValue('contact', selectedContact?.id || null)
  }, [selectedContact, form])

  useEffect(() => {
    const getInitialContact = async (id: string) => {
      try {
        const usersResponse = await fetch(`${URL}/users/${id}`)
        if (!usersResponse.ok) {
          throw new Error('Error fetching contacts data')
        }
        const contactData: UserResponse = await usersResponse.json()
        const contact = {
          id: contactData.id,
          displayName: contactData.displayName,
          email: contactData.email,
        }
        setSelectedContact(contact)
        form.reset(mapGroupToBaseFormData(groupData))
        resetAutocomplete(contact)
      } catch (error) {
        console.error('Error fetching contact data:', error)
        throw new Error('Error fetching contact data')
      } finally {
        setIsLoading(false)
      }
    }

    if (groupData.contact) {
      getInitialContact(groupData.contact.id)
    } else {
      form.reset(mapGroupToBaseFormData(groupData))
    }
  }, [groupData, form])

  useEffect(() => {
    if (debouncedInput.trim().length >= MIN_LENGTH) {
      getContacts(debouncedInput)
    } else {
      form.setValue('contact', null)
      setSelectedContact(null)
      setContacts([])
      setContactListItems([])
    }
  }, [debouncedInput, form])

  const handleCreateGroup = async (formData: GroupBaseFormData) => {
    try {
      setIsLoading(true)
      // eslint-disable-next-line unused-imports/no-unused-vars
      const { id, ...groupData } = {
        ...formData,
        contact: selectedContact ? { id: selectedContact.id, displayName: selectedContact.displayName } : null,
      }
      const createGroupData: CreateGroupData = {
        ...groupData,
        description: groupData.description,
        parent: null,
        roles: [],
        subgroups: [],
        users: [],
      }
      const response = await createGroup(createGroupData)
      router.push(`/groups/${response.id}`)
    } catch (error) {
      console.error('An error occurred while creating the group: ', error)
    } finally {
      setIsLoading(false)
    }
  }

  const handleUpdateGroup = async (formData: GroupBaseFormData) => {
    try {
      setIsLoading(true)
      const updateGroupData: UpdateGroupData = {
        ...formData,
        contact: selectedContact ? { id: selectedContact.id, displayName: selectedContact.displayName } : null,
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

  const getContacts = async (searchString: string) => {
    try {
      const queryParam = `displayName_like=${searchString}`
      const usersResponse = await fetch(`${URL}/users?${queryParam}`)
      if (!usersResponse.ok) {
        throw new Error('Error fetching contacts data')
      }
      const contactsData: UserResponse[] = await usersResponse.json()
      const contacts = contactsData.map(contact => ({
        id: contact.id,
        displayName: contact.displayName,
        email: contact.email,
      }))
      setContacts(contacts)
      setContactListItems(getContactListItems(contacts))
    } catch (error) {
      console.error('Error fetching contacts data:', error)
      throw new Error('Error fetching contacts data')
    } finally {
      setIsLoading(false)
    }
  }

  const handleContactInputChange = async (value: string) => {
    setIsContactListOpen(true)
    setContactInput(value)
  }

  const handleSelectContact = (newSelection: SelectItem) => {
    form.setValue('contact', newSelection.value, { shouldDirty: true })
    const selectedContact = contacts.find(contact => contact.id === newSelection.value)
    if (selectedContact) {
      setSelectedContact(selectedContact)
      setContactInput(selectedContact.displayName)
    }
  }

  const handleAutocompleteBlur = () => {
    if (selectedContact && contactInput !== selectedContact?.displayName) {
      setContactInput(selectedContact?.displayName)
    }
  }

  const handleSubmit = isEditMode ? handleUpdateGroup : handleCreateGroup
  const handleValidationErrors = (errors: FieldErrors<Group>) => console.error('Validation errors: ', errors)

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8 h-full">
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
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-0 relative">
            <AutoComplete
              className={cn('gap-2', isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}
              id="contact"
              popoverContentProps={{ className: 'w-[var(--radix-popover-trigger-width)]' }}
              isOpen={isContactListOpen}
              listItems={contactListItems}
              form={form}
              name="contact"
              placeholder={t('details.contact.placeholder')}
              label={t('details.contact.label')}
              inputValue={contactInput}
              minLength={MIN_LENGTH}
              onOpenChange={setIsContactListOpen}
              onInputChange={handleContactInputChange}
              onSelectItem={handleSelectContact}
              onBlur={handleAutocompleteBlur}
            />
          </DetailsFieldContainer>
        </ContentCard>
        <ActionButtons
          onCancelClick={() => form.reset()}
          confirmButtonType="submit"
          isConfirmButtonDisabled={!form.formState.isDirty && form.getValues().contact === groupData.contact?.id}
          isCancelButtonDisabled={!form.formState.isDirty}
        />
      </form>
    </Form>
  )
}
