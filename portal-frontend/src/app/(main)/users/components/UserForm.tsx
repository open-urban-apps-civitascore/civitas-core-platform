'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { SquarePen } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { useCreateUser, useUpdateUser } from '@/app/services/api/users/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Select } from '@/components/form/fields/Select'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useQueryParams } from '@/hooks/use-query-params'
import { User, UserFormData, UserFormSchema } from '@/types/users'
import { mapUserToFormData } from '@/utils/users'

export type FormUser = Omit<User, 'roles'>

interface UserFormProps {
  userData: User
  isEditMode?: boolean
}

export const UserForm = (props: UserFormProps) => {
  const { userData, isEditMode = false } = props
  const router = useRouter()
  const t = useTranslations('users')
  const tCommon = useTranslations('common')

  const createUser = useCreateUser()
  const updateUser = useUpdateUser()

  const { getApiRequestParamsByUrl } = useQueryParams()
  const [defaultUserData, setDefaultUserData] = useState(userData)
  const [isReadOnly, setIsReadOnly] = useState(isEditMode)

  const isLoading = createUser.isPending || updateUser.isPending

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

  const form = useForm<UserFormData>({
    resolver: zodResolver(UserFormSchema),
    defaultValues: mapUserToFormData(defaultUserData),
  })

  useEffect(() => {
    form.reset(mapUserToFormData(defaultUserData))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultUserData])

  const watch = form.watch()

  // checks if changed phone number without whitespaces is the same as initial phone number or if any other field has been changed
  const isFormDirty = useMemo(() => {
    const dirtyFields = form.formState.dirtyFields
    const isPhoneFieldDirty =
      dirtyFields.phone && form.getValues('phone')?.replace(/\s+/g, '') !== defaultUserData.phone?.replace(/\s+/g, '')
    const isNonPhoneFieldDirty = Object.keys(dirtyFields).find(field => field !== 'phone')
    return isNonPhoneFieldDirty || isPhoneFieldDirty
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watch, form, defaultUserData.phone])

  const handleCancelClick = () => {
    const apiParams = getApiRequestParamsByUrl()
    router.push(`/users?${apiParams}`)
  }

  const handleCreateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)

    const mappedData: User = { ...parsed, groups: defaultUserData.groups }
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createUserData } = mappedData
    createUser.mutate(createUserData, {
      onSuccess: ({ data }) => {
        router.push(`/users/${data.id}`)
      },
    })
  }

  const handleUpdateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)

    const updateUserData = { ...parsed, groups: defaultUserData.groups }
    updateUser.mutate(updateUserData, {
      onSuccess: ({ data }) => {
        setDefaultUserData(data)
        setIsReadOnly(true)
      },
    })
  }

  const handleSubmit = isEditMode ? form.handleSubmit(handleUpdateUser) : form.handleSubmit(handleCreateUser)

  const EditButton = (
    <Button data-testid="editButton" variant="outline" type="button" onClick={() => setIsReadOnly(false)}>
      <SquarePen />
      {tCommon('actions.edit')}
    </Button>
  )

  return (
    <PageBackground hasBackground={!isReadOnly}>
      <Form {...form}>
        <form onSubmit={handleSubmit} data-testid="userDetailsForm">
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
          {!isReadOnly && !isLoading && (
            <ActionButtons
              confirmButtonType="submit"
              isConfirmButtonDisabled={!isFormDirty}
              onCancelClick={handleCancelClick}
              hasCard
              className="mt-6"
            />
          )}
        </form>
      </Form>
    </PageBackground>
  )
}
