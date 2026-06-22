'use client'

import { useTranslations } from 'next-intl'
import { PathValue, UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { AutoComplete } from '@/components/form/fields/AutoComplete'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { useContactAutocomplete } from '@/hooks/use-contact-autocomplete'
import { useIsMobile } from '@/hooks/use-mobile'
import { usePermissions } from '@/hooks/use-permissions'
import { cn } from '@/lib/utils'
import { ItemType } from '@/types/common'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { GroupBaseFormData } from '@/types/groups'

interface BaseInfoTabProps {
  form: UseFormReturn<GroupBaseFormData>
  isReadOnly: boolean
  initialContactUser: ItemType | null
}

const MIN_LENGTH = 3

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { form, isReadOnly, initialContactUser } = props
  const t = useTranslations('groups')
  const isMobile = useIsMobile()
  const { hasPermission } = usePermissions()
  const canReadUsers = hasPermission(PERMISSION_NAMES.USER_READ)
  const isContactDisabled = isReadOnly || !canReadUsers

  const {
    contacts,
    contactInput,
    isContactListOpen,
    isLoadingContacts,
    setIsContactListOpen,
    handleContactInputChange,
    handleSelectContact,
    handleAutocompleteBlur,
  } = useContactAutocomplete({
    form,
    fieldName: 'contactUserId',
    initialContact: initialContactUser,
    emptyValue: '' as PathValue<GroupBaseFormData, 'contactUserId'>,
  })

  const contactListItems = contacts.map(c => ({
    value: c.id,
    label: (
      <div>
        <p>{c.displayName}</p>
        <p className="font-xs opacity-60">{c.email}</p>
      </div>
    ),
  }))

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
          onSelectItem={item => handleSelectContact(item.value)}
          onBlur={handleAutocompleteBlur}
          isLoading={isLoadingContacts}
          disabled={isContactDisabled}
        />
      </DetailsFieldContainer>
    </ContentCard>
  )
}
