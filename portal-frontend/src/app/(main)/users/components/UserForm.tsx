'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { SquarePen } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Select } from '@/components/form/fields/Select'
import { Switch } from '@/components/form/fields/Switch'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useQueryParams } from '@/hooks/useQueryParams'
import { cn } from '@/lib/utils'
import { Authority, UserFormData, UserFormSchema, UserResponse } from '@/types/users'
import { mapFormUserToApiData, mapUserToFormData } from '@/utils/users'

import { createUser, updateUser } from '../actions'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export type FormUser = Omit<UserResponse, 'authority' | 'department' | 'position' | 'positionDescription' | 'roles'> & {
  authority: string
  department: string
  positionDescription: string
}

interface UserFormProps {
  userData: UserResponse
  isEditMode?: boolean
}

export const UserForm = (props: UserFormProps) => {
  const { userData, isEditMode = false } = props
  const router = useRouter()
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const { getApiRequestParamsByUrl } = useQueryParams()
  const [defaultUserData, setDefaultUserData] = useState(userData)
  const [isReadOnly, setIsReadOnly] = useState(isEditMode)

  const [authorities, setAuthorities] = useState<Authority[]>([])

  const getAuthoritiesData = async () => {
    try {
      const authoritiesResponse = await fetch(`${URL}/authorities`, {
        cache: 'no-store',
      })
      if (!authoritiesResponse) {
        throw new Error('An error occurred while loading form data')
      }

      const authoritiesData: Authority[] = await authoritiesResponse.json()

      setAuthorities(authoritiesData)
    } catch (error) {
      console.error(error)
      throw new Error('An error occurred while loading form data')
    }
  }

  useEffect(() => {
    getAuthoritiesData()
  }, [])

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
  ] as const

  const form = useForm<UserFormData>({
    resolver: zodResolver(UserFormSchema),
    defaultValues: mapUserToFormData(defaultUserData),
  })

  useEffect(() => {
    form.reset(mapUserToFormData(defaultUserData))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultUserData])

  // eslint-disable-next-line @typescript-eslint/naming-convention
  const watchStatus = form.watch('active')
  const watchAuthority = form.watch('authority')
  const watch = form.watch()

  // adjust selected department when authority gets changed
  // remove department when new authority selected, reset initial department when initial aurhority selected
  useEffect(() => {
    if (watchAuthority !== defaultUserData.authority?.id) {
      form.setValue('department', '')
    } else {
      form.resetField('department')
    }
  }, [watchAuthority, form, defaultUserData])

  // checks if changed phone number without whitespaces is the same as initial phone number or if any other field has been changed
  const isFormDirty = useMemo(() => {
    const dirtyFields = form.formState.dirtyFields
    const isPhoneFieldDirty =
      dirtyFields.phone && form.getValues('phone').replace(/\s+/g, '') !== defaultUserData.phone.replace(/\s+/g, '')
    const isNonPhoneFieldDirty = Object.keys(dirtyFields).find(field => field !== 'phone')
    return isNonPhoneFieldDirty || isPhoneFieldDirty
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watch, form, defaultUserData.phone])

  // set form options for field 'department' depending on the currently selected authority
  const departmentOptions = useMemo(() => {
    const currentAuthority = authorities.find(authority => authority.id === watchAuthority)
    const departments =
      currentAuthority?.departments?.map(department => ({
        value: department.id,
        label: department.title,
      })) ?? []
    return departments
  }, [watchAuthority, authorities])

  const handleCancelClick = () => {
    const apiParams = getApiRequestParamsByUrl()
    router.push(`/users?${apiParams}`)
  }

  const handleCreateUser = async (formData: UserFormData) => {
    const mappedData: UserResponse = { ...mapFormUserToApiData(formData), groups: defaultUserData.groups }
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createUserData } = mappedData
    const newUser = await createUser(createUserData)
    if (!newUser) {
      throw new Error('An error occurred while creating the user')
    }
    router.push(`/users/${newUser.id}`)
  }

  const handleUpdateUser = async (formData: UserFormData) => {
    const updateUserData = { ...mapFormUserToApiData(formData), groups: defaultUserData.groups }
    await updateUser(updateUserData)
    setDefaultUserData(updateUserData)
    router.refresh()
    setIsReadOnly(true)
  }

  const handleSubmit = isEditMode ? form.handleSubmit(handleUpdateUser) : form.handleSubmit(handleCreateUser)

  const EditButton = (
    <Button data-testid="editButton" variant="outline" type="button" onClick={() => setIsReadOnly(false)}>
      <SquarePen />
      {tCommon('actions.edit')}
    </Button>
  )

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit} data-testid="userDetailsForm">
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-4 text-xl">
            <SubHeader
              title={t('info.header')}
              subtitle={t('info.subheader')}
              customElement={isEditMode && isReadOnly && EditButton}
            />
          </DetailsFieldContainer>
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
              placeholder={t('form.selectTitle')}
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
          <DetailsFieldContainer>
            <TextField
              form={form}
              label={t('info.phone')}
              name="phone"
              placeholder={t('info.phone')}
              disabled={isReadOnly}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer>
            <Select
              id="authority-select"
              label={t('info.authority')}
              options={authorities.map(authority => ({ value: authority.id, label: authority.title }))}
              placeholder={t('form.selectAuthority')}
              form={form}
              name="authority"
              disabled={isReadOnly}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer>
            <Select
              id="department-select"
              label={t('info.department')}
              options={departmentOptions}
              placeholder={t('form.selectDepartment')}
              form={form}
              name="department"
              disabled={isReadOnly}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer>
            <TextArea
              className="max-w-lg my-12"
              form={form}
              label={t('info.description')}
              name="positionDescription"
              placeholder={t('info.description')}
              disabled={isReadOnly}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 flex items-center">
            <Switch form={form} name="active" label={t('info.status.title')} isReadOnly={isReadOnly} />
            <span data-testid="activeStatus" className={cn('ml-3 text-sm', isReadOnly && 'text-muted-foreground')}>
              {watchStatus ? t('info.status.active') : t('info.status.inactive')}
            </span>
          </DetailsFieldContainer>
        </ContentCard>
        {!isReadOnly && (
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
  )
}
