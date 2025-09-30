'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { TextField } from '@/components/form/text-field/TextField'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'

import { AccessibleSelectProps, Select } from '@/components/form/text-field/Select'
import { UserFormData, UserFormSchema } from '../actions'
import { Category, UserResponse } from '../page'

export type FormUser = Omit<UserResponse, 'group' | 'authority' | 'department'> & {
  group: string | null
  authority: string
  department: string
}

export type Authority = Category & {
  departments: Category[]
}

interface UserFormProps {
  userData: FormUser
  userGroups: AccessibleSelectProps<FormUser>['options']
  isEditMode?: boolean
  authorities: Authority[]
}

export const UserForm = (props: UserFormProps) => {
  const { userData, userGroups, authorities, isEditMode = false } = props
  const t = useTranslations('users')
  const [user, setUser] = useState<FormUser>(userData)
  console.log('USERDATA', userData)
  console.log('userGroups', userGroups)

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
      ...user,
    },
  })

  const watchAuthority = form.watch('authority')

  const departmentOptions = useMemo(() => {
    const currentAuthority = authorities.find(authority => authority.id === watchAuthority)
    console.log('currentAuthority:', currentAuthority)
    const departments =
      currentAuthority?.departments?.map(department => ({
        value: department.id,
        label: department.title,
      })) ?? []
    console.log('departments:', departments)
    return departments
  }, [watchAuthority])

  const onSubmit = (values: UserFormData) => {
    console.log(form)
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <div className="grid grid-cols-2 gap-4 space-y-8 mb-4">
          <TextField form={form} label={t('info.id')} name="id" placeholder={t('info.id')} disabled />
          <Select
            id="title-select"
            label={t('info.title.title')}
            options={titleOptions}
            placeholder={t('form.selectTitle')}
            form={form}
            name="title"
            required
          />
          <TextField
            form={form}
            label={t('info.firstName')}
            name="firstName"
            placeholder={t('info.firstName')}
            required
          />
          <TextField form={form} label={t('info.lastName')} name="lastName" placeholder={t('info.lastName')} required />
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
          <Select
            id="usergroups-select"
            label={t('info.group')}
            options={userGroups}
            placeholder={t('form.selectGroup')}
            form={form}
            name="group"
          />
          <TextField form={form} label={t('info.email')} name="email" placeholder={t('info.lastName')} required />
          <TextField form={form} label={t('info.phone')} name="phone" placeholder={t('info.phone')} />
        </div>
        <div>
          <Button type="submit">Submit</Button>
        </div>
      </form>
    </Form>
  )
}
