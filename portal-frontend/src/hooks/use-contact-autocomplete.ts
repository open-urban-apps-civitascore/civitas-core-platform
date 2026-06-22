import { useEffect, useRef, useState } from 'react'
import { FieldValues, Path, PathValue, UseFormReturn } from 'react-hook-form'

import { useGetUsers } from '@/app/services/api/users/clientRequests'
import { useDebounce } from '@/hooks/use-debounce'
import { Contact } from '@/types/users'

const MIN_LENGTH = 3

interface UseContactAutocompleteOptions<TForm extends FieldValues> {
  form: UseFormReturn<TForm>
  fieldName: Path<TForm>
  initialContact?: { id: string; name: string } | null
  emptyValue?: PathValue<TForm, Path<TForm>>
}

export const useContactAutocomplete = <TForm extends FieldValues>({
  form,
  fieldName,
  initialContact,
  emptyValue = null as PathValue<TForm, Path<TForm>>,
}: UseContactAutocompleteOptions<TForm>) => {
  const [isContactListOpen, setIsContactListOpen] = useState(false)
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null)
  const [contacts, setContacts] = useState<Contact[]>([])
  const [contactInput, setContactInput] = useState(initialContact?.name || '')
  const debouncedInput = useDebounce(contactInput, 300)
  const hasUserInteracted = useRef(false)

  const isDirty = form.formState.isDirty

  useEffect(() => {
    if (!isDirty) {
      setContactInput(initialContact?.name || '')
      hasUserInteracted.current = false
    }
  }, [isDirty, initialContact?.name])

  useEffect(() => {
    setContactInput(initialContact?.name || '')
    hasUserInteracted.current = false
  }, [initialContact?.id, initialContact?.name])

  const { data: contactsData, isLoading: isLoadingContacts } = useGetUsers({
    params: new URLSearchParams({ q: debouncedInput }),
    isEnabled: debouncedInput.trim().length >= MIN_LENGTH,
  })

  useEffect(() => {
    const mappedContacts =
      contactsData?.data.map(user => ({
        id: user.id,
        displayName: `${user.firstName} ${user.lastName}`,
        email: user.email,
      })) || []
    setContacts(mappedContacts)
  }, [contactsData?.data])

  useEffect(() => {
    if (debouncedInput.trim().length < MIN_LENGTH) {
      setSelectedContact(null)
      setContacts([])
      if (hasUserInteracted.current) {
        form.setValue(fieldName, emptyValue, { shouldDirty: true })
      }
    }
  }, [debouncedInput, emptyValue, form, fieldName])

  const handleContactInputChange = (value: string) => {
    hasUserInteracted.current = true
    setIsContactListOpen(true)
    setContactInput(value)
  }

  const handleSelectContact = (id: string) => {
    form.setValue(fieldName, id as PathValue<TForm, Path<TForm>>, { shouldDirty: true })
    const found = contacts.find(c => c.id === id)
    if (found) {
      setSelectedContact(found)
      setContactInput(found.displayName)
    }
  }

  const handleAutocompleteBlur = () => {
    if (!contactInput.trim()) {
      form.setValue(fieldName, emptyValue, { shouldDirty: true })
      setSelectedContact(null)
      setContactInput('')
    } else if (selectedContact && contactInput !== selectedContact.displayName) {
      setContactInput(selectedContact.displayName)
    }
  }

  return {
    contacts,
    contactInput,
    isContactListOpen,
    isLoadingContacts,
    setIsContactListOpen,
    handleContactInputChange,
    handleSelectContact,
    handleAutocompleteBlur,
  }
}
