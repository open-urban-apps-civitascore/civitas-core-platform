'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { ChangeEvent, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { TextField } from '@/components/form/text-field/TextField'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { TitleSchemaType } from '@/types/users'

import { UserFormData, UserFormSchema } from '../actions'
import { AccessibleSelectProps, Select } from '@/components/form/text-field/Select'
import { Category, UserAuthority, UserResponse } from '../page'

export type FormUser = Omit<UserResponse, 'group' | 'authority' | 'department'> & {
  group: string | null
  authority: string
  department: string
}

export type Authority = Category & {
  department: Category[]
}

interface UserFormProps {
  userData: FormUser
  userGroups: AccessibleSelectProps<FormUser>['options']
  isEditMode?: boolean
  authorities: Authority[]
}

export const UserForm = (props: UserFormProps) => {
  const { userData, userGroups, authorities, isEditMode=false } = props
  const t = useTranslations('users')
  const [user, setUser] = useState<FormUser>(userData)
  console.log('USERDATA', userData)
  console.log('userGroups', userGroups)

  const form = useForm<UserFormData>({
    resolver: zodResolver(UserFormSchema),
    defaultValues: {
      ...user,
    },
  })

  const watchAuthority = form.watch('authority')

  const departmentOptions = useMemo(() => {
    const currentAuthority = authorities.find(authority => authority.id === watchAuthority)
    const departments =
      currentAuthority?.department?.map(department => ({
        value: department.id,
        label: department.title,
      })) ?? []
    return departments
  }, [watchAuthority])

  const onSubmit = (values: UserFormData) => {
    console.log(form)
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
        <TextField form={form} label={t('info.id')} name="id" placeholder="" disabled />
        <TextField form={form} label={t('info.firstName')} name="firstName" placeholder={t('info.firstName')} />
        <TextField form={form} label={t('info.lastName')} name="lastName" placeholder={t('info.lastName')} />
        <TextField form={form} label={t('info.authority')} name="authority" placeholder={t('info.authority')} />
        <TextField form={form} label={t('info.department')} name="department" placeholder={t('info.department')} />
        <Select
          id="authority-select"
          label={t('info.authority')}
          options={authorities.map(authority => ({ value: authority.id, label: authority.title }))}
          placeholder={t('info.selectAuthority')}
          form={form}
          name="authority"
        />
        <Select
          id="department-select"
          label={t('info.department')}
          options={departmentOptions}
          placeholder={t('info.selectDepartment')}
          form={form}
          name="department"
        />
        <Select
          id="usergroups-select"
          label={t('info.group')}
          options={userGroups}
          placeholder={t('info.selectGroup')}
          form={form}
          name="group"
        />
        <TextField form={form} label={t('info.email')} name="email" placeholder={t('info.lastName')} />
        <TextField form={form} label={t('info.phone')} name="phone" placeholder={t('info.phone')} />
        <Button type="submit">Submit</Button>
      </form>
    </Form>
  )
}
