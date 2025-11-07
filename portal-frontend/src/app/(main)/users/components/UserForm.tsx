'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { Select } from '@/components/form/fields/Select'
import { Switch } from '@/components/form/fields/Switch'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Authority, UserFormData, UserFormSchema, UserResponse } from '@/types/users'
import { mapFormUserToApiData, mapUserToFormData } from '@/utils/users'

import { createUser, updateUser } from '../actions'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { ContentCard } from '@/components/content-card/ContentCard'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { readonly } from 'zod'

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
      value: 'male',
      label: t('info.title.male'),
    },
    {
      value: 'female',
      label: t('info.title.female'),
    },
  ]

  const form = useForm<UserFormData>({
    resolver: zodResolver(UserFormSchema),
    defaultValues: mapUserToFormData(userData),
  })

  const watchAuthority = form.watch('authority')

  const departmentOptions = useMemo(() => {
    const currentAuthority = authorities.find(authority => authority.id === watchAuthority)
    const departments =
      currentAuthority?.departments?.map(department => ({
        value: department.id,
        label: department.title,
      })) ?? []
    return departments
  }, [watchAuthority, authorities])

  const goToUsersList = () => {
    const apiParams = getApiRequestParamsByUrl()
    router.push(`/users?${apiParams}`)
  }

  const handleCreateUser = (formData: UserFormData) => {
    const mappedData: UserResponse = { ...mapFormUserToApiData(formData), group: userData.group }
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createUserData } = mappedData
    createUser(createUserData)
    router.push('/users')
  }

  const handleUpdateUser = (formData: UserFormData) => {
    const updateUserData = { ...mapFormUserToApiData(formData), group: userData.group }
    updateUser(updateUserData)
    goToUsersList()
  }

  const handleSubmit = isEditMode ? form.handleSubmit(handleUpdateUser) : form.handleSubmit(handleCreateUser)

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit}>
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-8 text-xl">
            <h2>{t('info.header')}</h2>
            <p className="text-sm text-muted-foreground pt-1">{t('info.subheader')}</p>
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
          <DetailsFieldContainer className="border-b-0">
            <Switch form={form} name="active" label={t('info.active')} isReadOnly={isReadOnly} />
          </DetailsFieldContainer>
        </ContentCard>
        {!isReadOnly && (
          <ActionButtons confirmButtonType="submit" onCancelClick={goToUsersList} hasCard className="mt-6" />
        )}
      </form>
    </Form>
  )
}
