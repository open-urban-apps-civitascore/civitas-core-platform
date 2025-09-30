'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'

import { useQueryParams } from '@/hooks/useQueryParams'

import { FormRole, RoleInput, roleSchema, RoleType } from '../../../../../types/roles'
import { RolesForm } from '../components/RolesForm'
import { DEFAULT_TAB } from '../page'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const CreateRolePage = () => {
  const t = useTranslations('roles')

  const router = useRouter()

  const { setTabValueParam, tabValue } = useQueryParams()

  const form = useForm<FormRole>({
    resolver: zodResolver(roleSchema),
    defaultValues: {
      name: '',
      description: '',
    },
  })

  const postRole = async (roleInput: RoleInput): Promise<void> => {
    try {
      const response = await fetch(`${URL}/roles`, {
        method: 'POST',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(roleInput),
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      form.reset()

      setTabValueParam(tabValue || DEFAULT_TAB)
      router.push(`/roles?_tab=${tabValue || DEFAULT_TAB}`)
    } catch (error) {
      console.error('Fehler:', error)
    }
  }

  const onSubmit = (values: FormRole) => {
    postRole({
      type: (tabValue as RoleType) || (DEFAULT_TAB as RoleType),
      tenant: 'ExampleCorp', // Placeholder tenant
      user: null, // null until user assignment is implemented
      permissions: null, // null until permissions are implemented
      createdAt: new Date().toISOString(), // Placeholder createdAt, later set by backend
      ...values,
    })
  }

  return (
    <div>
      <h1>{t('form.title.createRole')}</h1>
      <RolesForm form={form} onSubmit={onSubmit} isEdit={false} />
    </div>
  )
}

export default CreateRolePage
