'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { AutoComplete } from '@/components/form/fields/AutoComplete'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { useContactAutocomplete } from '@/hooks/use-contact-autocomplete'
import { useIsMobile } from '@/hooks/use-mobile'
import { usePermissions } from '@/hooks/use-permissions'
import { cn } from '@/lib/utils'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Datapool, DatapoolFormData } from '@/types/datapools'

interface BasicInfoTabProps {
  form: UseFormReturn<DatapoolFormData>
  isReadOnly?: boolean
  datapool: Datapool
}

const MIN_LENGTH = 3

export const BasicInfoTab = (props: BasicInfoTabProps) => {
  const { form, isReadOnly = false, datapool } = props
  const isMobile = useIsMobile()
  const t = useTranslations('datapools')
  const tCommon = useTranslations('common')
  const { hasPermission } = usePermissions()
  const canReadUsers = hasPermission(PERMISSION_NAMES.USER_READ)

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
    fieldName: 'contactPersonId',
    initialContact: datapool.contactPerson
      ? { id: datapool.contactPerson.id, name: datapool.contactPerson.name }
      : null,
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
    <ContentCard className={cn('h-full overflow-auto')}>
      <div className="max-w-300 flex flex-col gap-2 pt-2" data-testid="basicInfoTab">
        <DetailsFieldContainer className="pt-0 border-b-0">
          <SubHeader title={t('form.title')} titleClassName="text-2xl leading-none font-bold" />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <TextField
            id="datapoolName"
            form={form}
            label={t('form.name')}
            name="name"
            placeholder={t('form.namePlaceholder')}
            disabled={isReadOnly}
            required
          />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <FormTextArea
            form={form}
            name="description"
            label={t('form.description')}
            placeholder={t('form.descriptionPlaceholder')}
            maxLength={150}
            hasCharacterCount
            disabled={isReadOnly}
            className="min-h-[100px] resize-none"
            hint={tCommon('info.descriptionHint')}
            required
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
            name="contactPersonId"
            placeholder={t('form.contactPlaceholder')}
            label={t('form.contact')}
            inputValue={contactInput}
            minLength={MIN_LENGTH}
            onOpenChange={setIsContactListOpen}
            onInputChange={handleContactInputChange}
            onSelectItem={item => handleSelectContact(item.value)}
            isLoading={isLoadingContacts}
            disabled={isReadOnly || !canReadUsers}
            onBlur={handleAutocompleteBlur}
          />
        </DetailsFieldContainer>
      </div>
    </ContentCard>
  )
}
