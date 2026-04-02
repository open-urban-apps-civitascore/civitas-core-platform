'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Form } from '@/components/ui/form'
import { User, UserFormData } from '@/types/users'

export type FormUser = Omit<User, 'roles'>

interface UserBasicInfoTabProps {
  userData: User
  form: UseFormReturn<UserFormData>
  isReadOnly: boolean
  isLoading: boolean
}

export const UserBasicInfoTab = (props: UserBasicInfoTabProps) => {
  const { form, isReadOnly, isLoading } = props
  const t = useTranslations('users')

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

  return (
    <Form {...form}>
      <form onSubmit={e => e.preventDefault()} data-testid="userDetailsForm">
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-4 ">
            <SubHeader title={t('info.header')} subtitle={t('info.subheader')} />
          </DetailsFieldContainer>
          {isLoading ? (
            <LoadingSpinner />
          ) : (
            <>
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
                <FormSelect
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
  )
}
