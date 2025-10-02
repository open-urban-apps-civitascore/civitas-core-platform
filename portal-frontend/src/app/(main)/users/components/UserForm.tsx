'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { TextField } from '@/components/form/fields/TextField'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { Category, UpdateUserData, UserFormData, UserFormSchema, UserResponse } from '@/types/users'

import { Select } from '@/components/form/fields/Select'
import { TextArea } from '@/components/form/fields/TextArea'
import { createUser, updateUser } from '../actions'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export type FormUser = Omit<UserResponse, 'authority' | 'department' | 'position' | 'positionDescription'> & {
  authority: string
  department: string
  position: string
  positionDescription: string
}

export type Authority = Category & {
  departments: Category[]
}

interface UserFormProps {
  userData: FormUser
  isEditMode?: boolean
}

export const UserForm = (props: UserFormProps) => {
  const { userData, isEditMode = false } = props
  const t = useTranslations('users')

  const [authorities, setAuthorities] = useState<Authority[]>([])

  const getFormData = async () => {
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
      throw new Error('An error occurred while loading form data')
    }
  }

  useEffect(() => {
    getFormData()
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
    defaultValues: {
      ...userData,
    },
  })

  console.log(form.getValues())

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

  const mapApiUserData = (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)

    const userData = {
      ...parsed,
      group: parsed.group ?? null,
      authority: parsed.authority
        ? { id: parsed.authority, department: parsed.department ? { id: parsed.department ?? null } : null }
        : null,
      position: parsed.position ?? null,
      positionDescription: parsed.position ?? null,
      displayName: `${parsed.firstName} ${parsed.lastName}`,
    }
    return userData
  }

  const handleCreateUser = (userData: UserFormData) => {
    console.log('handleCreateUser: ', userData)
    const mappedData = mapApiUserData(userData)
    const { id, ...createUserData } = mappedData
    createUser(createUserData)
    form.reset()
  }


  const handleUpdateUser = (userData: UserFormData) => {
    console.log('handleUpdateUser: ', userData)
    const updateUserData = mapApiUserData(userData)
    updateUser(updateUserData)
  }

  const handleSubmit = isEditMode ? form.handleSubmit(handleUpdateUser) : form.handleSubmit(handleCreateUser)

  return (
    <Form {...form}>
      <form onSubmit={handleSubmit}>
        <div className="grid grid-cols-2 gap-4 space-y-8 mb-4">
          <TextField form={form} label={t('info.id')} name="id" placeholder={t('info.id')} disabled />
          <div className='flex w-full justify-between'>
            <Select
              id="title-select"
              label={t('info.title.title')}
              options={titleOptions}
              placeholder={t('form.selectTitle')}
              form={form}
              name="title"
              required
            />
            <Switch form={form} name="active" label={t('info.active')} />
          </div>
          <TextField
            form={form}
            label={t('info.firstName')}
            name="firstName"
            placeholder={t('info.firstName')}
            required
          />
          <TextField form={form} label={t('info.lastName')} name="lastName" placeholder={t('info.lastName')} required />
          <TextField form={form} label={t('info.email')} name="email" placeholder={t('info.email')} required />
          <TextField form={form} label={t('info.phone')} name="phone" placeholder={t('info.phone')} />
          <Select
            id="authority-select"
            label={t('info.authority')}
            options={authorities.map(authority => ({ value: authority.id, label: authority.title }))}
            placeholder={t('form.selectAuthority')}
            form={form}
            name="authority"
          />
          <Select
            id="department-select"
            label={t('info.department')}
            options={departmentOptions}
            placeholder={t('form.selectDepartment')}
            form={form}
            name="department"
          />
          <TextField form={form} label={t('info.position')} name="position" placeholder={t('info.position')} />
          <TextArea
            form={form}
            label={t('info.description')}
            name="positionDescription"
            placeholder={t('info.description')}
          />
        </div>
        <div>
          <Button type="submit">Submit</Button>
        </div>
      </form>
    </Form>
  )
}
