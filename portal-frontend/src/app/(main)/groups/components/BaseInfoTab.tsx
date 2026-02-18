'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { FieldErrors, useForm } from 'react-hook-form'

import { useCreateGroup, useUpdateGroup } from '@/app/services/api/groups/clientRequests'
import { useGetUsers } from '@/app/services/api/users/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { AutoComplete, SelectItem } from '@/components/form/fields/AutoComplete'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { Form } from '@/components/ui/form'
import { useDebounce } from '@/hooks/use-debounce'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { Group, GroupBaseFormData, GroupBaseFormDataSchema } from '@/types/groups'
import { Contact } from '@/types/users'
import { mapGroupApiToFormData } from '@/utils/groups'

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
interface BaseInfoTabProps {
  groupData: Group
  isEditMode: boolean
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { groupData, isEditMode } = props
  const t = useTranslations('groups')
  const isMobile = useIsMobile()
  const router = useRouter()
  const createGroup = useCreateGroup()
  const updateGroup = useUpdateGroup()
  console.log('group data', groupData)

  const [defaultFormData, setDefaultFormData] = useState(mapGroupApiToFormData(groupData))
  const [isContactListOpen, setIsContactListOpen] = useState(false)
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null)
  const [contacts, setContacts] = useState<Contact[]>([])
  const [contactListItems, setContactListItems] = useState<SelectItem[]>([])
  const [contactInput, setContactInput] = useState(groupData.contactUser?.name || '')
  const debouncedInput = useDebounce(contactInput, 300)
  // eslint-disable-next-line @typescript-eslint/naming-convention
  const getUsersParams = new URLSearchParams({ displayName_like: debouncedInput })

  const { data: contactsData, isLoading: isLoadingContacts } = useGetUsers({
    params: getUsersParams,
    isEnabled: debouncedInput.trim().length >= MIN_LENGTH,
  })

  useEffect(() => {
    const contacts =
      contactsData?.data.map(contact => ({
        id: contact.id,
        displayName: `${contact.firstName} ${contact.lastName}`,
        email: contact.email,
      })) || []
    setContacts(contacts)
    setContactListItems(getContactListItems(contacts))
  }, [contactsData?.data])

  const form = useForm<GroupBaseFormData>({
    resolver: zodResolver(GroupBaseFormDataSchema),
    defaultValues: defaultFormData,
  })

  useEffect(() => {
    console.log('form values', form.getValues())
    form.setValue('contactUserId', selectedContact?.id || '')
  }, [selectedContact, form])

  useEffect(() => {
    form.reset(defaultFormData)
  }, [defaultFormData, form])

  useEffect(() => {
    if (debouncedInput.trim().length < MIN_LENGTH) {
      form.setValue('contactUserId', '')
      setSelectedContact(null)
      setContacts([])
      setContactListItems([])
    }
  }, [debouncedInput, form])

  const handleCreateGroup = async (formData: GroupBaseFormData) => {
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = formData
    createGroup.mutate(createGroupData, { onSuccess: ({ data }) => router.push(`/groups/${data.id}`) })
  }

  const handleUpdateGroup = async (formData: GroupBaseFormData) => {
    updateGroup.mutate(formData, { onSuccess: ({ data }) => setDefaultFormData(mapGroupApiToFormData(data)) })
  }

  const handleContactInputChange = async (value: string) => {
    setIsContactListOpen(true)
    setContactInput(value)
  }

  const handleSelectContact = (newSelection: SelectItem) => {
    form.setValue('contactUserId', newSelection.value, { shouldDirty: true })
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
            <TextField form={form} label={t('details.name')} name="name" placeholder={t('details.name')} required />
          </DetailsFieldContainer>

          <DetailsFieldContainer>
            <FormTextArea
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
              name="contactUserId"
              placeholder={t('details.contact.placeholder')}
              label={t('details.contact.label')}
              inputValue={contactInput}
              minLength={MIN_LENGTH}
              onOpenChange={setIsContactListOpen}
              onInputChange={handleContactInputChange}
              onSelectItem={handleSelectContact}
              onBlur={handleAutocompleteBlur}
              isLoading={isLoadingContacts}
            />
          </DetailsFieldContainer>
        </ContentCard>
        <ActionButtons
          onCancelClick={() => router.push('/groups')}
          confirmButtonType="submit"
          isConfirmButtonDisabled={
            !form.formState.isDirty && form.getValues().contactUserId === defaultFormData.contactUserId
          }
        />
      </form>
    </Form>
  )
}
