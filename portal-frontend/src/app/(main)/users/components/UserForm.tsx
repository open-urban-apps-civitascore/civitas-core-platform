'use client'

import { SquarePen } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { Dispatch, SetStateAction, useEffect, useMemo } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Select } from '@/components/form/fields/Select'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { User, UserFormData } from '@/types/users'

export type FormUser = Omit<User, 'roles'>

interface UserFormProps {
  userData: User
  isEditMode?: boolean
  form: UseFormReturn<UserFormData>
  isReadOnly: boolean
  setIsReadOnly: Dispatch<SetStateAction<boolean>>
  isLoading: boolean
  defaultUserData: User | null
  setIsSaveButtonDisabled: Dispatch<SetStateAction<boolean>>
}

export const UserForm = (props: UserFormProps) => {
  const {
    isEditMode = false,
    form,
    isReadOnly,
    setIsReadOnly,
    isLoading,
    defaultUserData,
    setIsSaveButtonDisabled,
  } = props
  const t = useTranslations('users')
  const tCommon = useTranslations('common')

  const titleOptions = [
    {
      value: 'MR',
      label: t('info.title.mr'),
    },
    {
      value: 'MS',
      label: t('info.title.ms'),
    },
    {
      value: 'OTHER',
      label: t('info.title.other'),
    },
  ]

  const watch = form.watch()

  const isFormDirty = useMemo(() => {
    const dirtyFields = form.formState.dirtyFields
    const isPhoneFieldDirty =
      dirtyFields.phone && form.getValues('phone')?.replace(/\s+/g, '') !== defaultUserData?.phone?.replace(/\s+/g, '')
    const isNonPhoneFieldDirty = Object.keys(dirtyFields).find(field => field !== 'phone')
    return isNonPhoneFieldDirty || isPhoneFieldDirty
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watch, form, defaultUserData?.phone])

  useEffect(() => {
    setIsSaveButtonDisabled(!isFormDirty)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isFormDirty])

  const EditButton = (
    <Button data-testid="editButton" variant="outline" type="button" onClick={() => setIsReadOnly(false)}>
      <SquarePen />
      {tCommon('actions.edit')}
    </Button>
  )

  return (
    <PageBackground hasBackground={!isReadOnly}>
      <Form {...form}>
        <form onSubmit={e => e.preventDefault()} data-testid="userDetailsForm">
          <ContentCard>
            <DetailsFieldContainer className="pt-0 pb-4 text-xl">
              <SubHeader
                title={t('info.header')}
                subtitle={t('info.subheader')}
                customElement={isEditMode && isReadOnly && !isLoading && EditButton}
              />
            </DetailsFieldContainer>
            {isLoading ? (
              <LoadingSpinner />
            ) : (
              <>
                {' '}
                <DetailsFieldContainer>
                  <TextField
                    form={form}
                    label={t('info.id')}
                    name="id"
                    placeholder={t('info.id')}
                    disabled
                    readOnly={isReadOnly}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <Select
                    id="title-select"
                    label={t('info.title.title')}
                    options={titleOptions}
                    placeholder={t('info.selectTitle')}
                    form={form}
                    name="title"
                    required={!isReadOnly}
                    disabled={isReadOnly}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <TextField
                    form={form}
                    label={t('info.firstName')}
                    name="firstName"
                    placeholder={t('info.firstName')}
                    required={!isReadOnly}
                    disabled={isReadOnly}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <TextField
                    form={form}
                    label={t('info.lastName')}
                    name="lastName"
                    placeholder={t('info.lastName')}
                    required={!isReadOnly}
                    disabled={isReadOnly}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <TextField
                    form={form}
                    label={t('info.email')}
                    name="email"
                    placeholder={t('info.email')}
                    required={!isReadOnly}
                    disabled={isReadOnly}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0">
                  <TextField
                    form={form}
                    label={t('info.phone')}
                    name="phone"
                    placeholder={t('info.phone')}
                    disabled={isReadOnly}
                  />
                </DetailsFieldContainer>
              </>
            )}
          </ContentCard>
        </form>
      </Form>
    </PageBackground>
  )
}
