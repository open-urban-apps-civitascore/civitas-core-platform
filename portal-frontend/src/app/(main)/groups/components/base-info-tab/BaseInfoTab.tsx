'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { useGetUsers } from '@/app/services/api/users/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { AutoComplete, SelectItem } from '@/components/form/fields/AutoComplete'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { useDebounce } from '@/hooks/use-debounce'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { Item2 } from '@/types/common'
import { GroupBaseFormData } from '@/types/groups'
import { Contact } from '@/types/users'

const MIN_LENGTH = 3

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
  form: UseFormReturn<GroupBaseFormData>
  isReadOnly: boolean
  initialContactUser: Item2 | null
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { form, isReadOnly, initialContactUser } = props
  const t = useTranslations('groups')
  const isMobile = useIsMobile()

  const [isContactListOpen, setIsContactListOpen] = useState(false)
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null)
  const [contacts, setContacts] = useState<Contact[]>([])
  const [contactListItems, setContactListItems] = useState<SelectItem[]>([])
  const [contactInput, setContactInput] = useState(initialContactUser?.name || '')
  const debouncedInput = useDebounce(contactInput, 300)
  const getUsersParams = new URLSearchParams({ q: debouncedInput })

  useEffect(() => {
    setContactInput(initialContactUser?.name || '')
  }, [initialContactUser])
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

  useEffect(() => {
    form.setValue('contactUserId', selectedContact?.id || '')
  }, [selectedContact, form])

  useEffect(() => {
    if (debouncedInput.trim().length < MIN_LENGTH) {
      form.setValue('contactUserId', '')
      setSelectedContact(null)
      setContacts([])
      setContactListItems([])
    }
  }, [debouncedInput, form])

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

  return (
    <ContentCard>
      <DetailsFieldContainer className="pt-0 pb-3 text-xl">
        <h2>{t('details.baseInfo')}</h2>
      </DetailsFieldContainer>
      <DetailsFieldContainer>
        <TextField
          form={form}
          label={t('details.name')}
          name="name"
          placeholder={t('details.name')}
          disabled={isReadOnly}
          required
        />
      </DetailsFieldContainer>

      <DetailsFieldContainer>
        <FormTextArea
          className="max-w-lg"
          form={form}
          label={t('details.description')}
          name="description"
          placeholder={t('details.description')}
          formItemProps={{
            className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
          }}
          disabled={isReadOnly}
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
          disabled={isReadOnly}
        />
      </DetailsFieldContainer>
    </ContentCard>
  )
}
