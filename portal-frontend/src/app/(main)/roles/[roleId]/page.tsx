'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useParams, useRouter } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import { useQueryParams } from '@/hooks/useQueryParams'

import { FormRole, RoleResponse, roleSchema, RoleUpdate } from '../../../../../types/roles'
import { RolesForm } from '../components/RolesForm'
import { DEFAULT_TAB } from '../page'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const EditRolePage = () => {
  const router = useRouter()

  const params = useParams<{ roleId: string }>()

  const { roleId } = params

  const { setTabValueParam } = useQueryParams()

  const [selectedRole, setSelectedRole] = useState<RoleResponse | undefined>(undefined)

  const form = useForm<FormRole>({
    resolver: zodResolver(roleSchema),
    defaultValues: {
      name: '',
      description: '',
    },
  })

  const getRole = useCallback(async (roleId: RoleResponse['id']) => {
    try {
      const response = await fetch(`${URL}/roles/${roleId}`, {
        method: 'GET',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
      })
      const data = await response.json()
      setSelectedRole(data)
    } catch (error) {
      console.error('Error fetching role:', error)
    }
  }, [])

  useEffect(() => {
    if (roleId) {
      getRole(roleId)
    }
  }, [roleId, getRole])

  useEffect(() => {
    if (selectedRole) {
      form.setValue('name', selectedRole.name)
      form.setValue('description', selectedRole.description || '')
    }
  }, [selectedRole, form])

  const updateRole = async (roleId: RoleResponse['id'], updatedRoleData: RoleUpdate) => {
    try {
      const response = await fetch(`${URL}/roles/${roleId}`, {
        method: 'PUT',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updatedRoleData),
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      const data = await response.json()
      console.log('Erfolgreich aktualisiert:', data)

      setTabValueParam(selectedRole?.type || DEFAULT_TAB)
      router.push(`/roles?_tab=${selectedRole?.type || DEFAULT_TAB}`)
    } catch (error) {
      console.error('Fehler:', error)
    }
  }

  const deleteRole = async (roleId: RoleResponse['id']) => {
    try {
      const response = await fetch(`${URL}/roles/${roleId}`, {
        method: 'DELETE',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      router.push('/roles')
    } catch (error) {
      console.error('Fehler:', error)
    }
  }

  const onSubmit = (values: FormRole) => {
    if (!selectedRole) return

    updateRole(roleId, { ...selectedRole, ...values })
  }

  return (
    <div>
      <h1>{selectedRole?.name}</h1>
      <RolesForm form={form} onSubmit={onSubmit} isEdit={true} deleteRole={() => deleteRole(roleId)} />
    </div>
  )
}

export default EditRolePage
