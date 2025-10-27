'use client'

import { Loader2, Plus, X } from 'lucide-react'
import { useTranslations } from 'next-intl'
import React, { useEffect, useState } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { UserResponse } from '@/types/users'

interface Role {
  id: string
  name: string
  description: string
  type: 'System' | 'Data' | 'Governance'
}

interface RolesTabProps {
  userId: string
}

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const RolesTab = ({ userId }: RolesTabProps) => {
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const tRoles = useTranslations('roles')
  const [user, setUser] = useState<UserResponse | null>(null)
  const [originalRoles, setOriginalRoles] = useState<string[]>([])
  const [allRoles, setAllRoles] = useState<Role[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [isAddRoleOpen, setIsAddRoleOpen] = useState(false)
  const [selectedCategory, setSelectedCategory] = useState<'System' | 'Data' | 'Governance'>('System')

  const hasChanges = user ? JSON.stringify(user.roles.sort()) !== JSON.stringify(originalRoles.sort()) : false

  // Fetch user data and all roles
  useEffect(() => {
    const fetchData = async () => {
      try {
        const [userResponse, rolesResponse] = await Promise.all([
          fetch(`${URL}/users/${userId}`),
          fetch(`${URL}/roles`),
        ])

        if (!userResponse.ok || !rolesResponse.ok) {
          throw new Error('Failed to fetch data')
        }

        const userData = await userResponse.json()
        const rolesData = await rolesResponse.json()

        setUser(userData)
        setOriginalRoles(userData.roles)
        setAllRoles(rolesData)
      } catch (error) {
        console.error('Error fetching data:', error)
      } finally {
        setIsLoading(false)
      }
    }

    fetchData()
  }, [userId])

  // Get roles by category
  const getRolesByCategory = (category: 'System' | 'Data' | 'Governance') => {
    if (!user || !allRoles) return []

    const userRoles = allRoles.filter(role => user.roles.includes(role.id) && role.type === category)
    return userRoles
  }

  // Get available roles for adding (not already assigned)
  const getAvailableRolesByCategory = (category: 'System' | 'Data' | 'Governance') => {
    if (!user || !allRoles) return []

    return allRoles.filter(role => !user.roles.includes(role.id) && role.type === category)
  }

  // Add role to user
  const addRole = (roleId: string) => {
    if (!user) return
    const updatedRoles = [...user.roles, roleId]
    setUser({ ...user, roles: updatedRoles })
  }

  // Remove role from user
  const removeRole = (roleId: string) => {
    if (!user) return
    const updatedRoles = user.roles.filter(id => id !== roleId)
    setUser({ ...user, roles: updatedRoles })
  }

  // Save changes to server
  const handleSave = async () => {
    if (!user) return

    setIsSaving(true)
    try {
      const response = await fetch(`${URL}/users/${userId}`, {
        method: 'PATCH',
        headers: {
          ['Content-Type']: 'application/json',
        },
        body: JSON.stringify({ roles: user.roles }),
      })

      if (!response.ok) {
        throw new Error('Failed to save roles')
      }

      setOriginalRoles(user.roles)
    } catch (error) {
      console.error('Error saving roles:', error)
    } finally {
      setIsSaving(false)
    }
  }

  // Cancel changes
  const handleCancel = () => {
    if (!user) return
    setUser({ ...user, roles: originalRoles })
  }

  const RoleCategory = ({
    title,
    category,
    colorClass,
  }: {
    title: string
    category: 'System' | 'Data' | 'Governance'
    colorClass: string
  }) => {
    const categoryRoles = getRolesByCategory(category)
    const availableRoles = getAvailableRolesByCategory(category)

    return (
      <div className="mb-8">
        <h3 className="text-lg font-semibold mb-4">{title}</h3>
        <div className="flex flex-wrap gap-2 items-center">
          {categoryRoles.map(role => (
            <Badge key={role.id} variant="secondary" className={`${colorClass} relative group`}>
              <span>{role.name}</span>
              <Button
                variant="ghost"
                size="sm"
                className="h-4 w-4 p-0 ml-2 opacity-70 hover:opacity-100"
                onClick={() => removeRole(role.id)}
                disabled={isSaving}
              >
                <X className="h-3 w-3" />
              </Button>
            </Badge>
          ))}

          {availableRoles.length > 0 && (
            <Dialog
              open={isAddRoleOpen && selectedCategory === category}
              onOpenChange={open => {
                setIsAddRoleOpen(open)
                if (open) setSelectedCategory(category)
              }}
            >
              <DialogTrigger asChild>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-6 w-6 p-0 rounded-full"
                  onClick={() => setSelectedCategory(category)}
                >
                  <Plus className="h-4 w-4" />
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>{t('rolesTab.addRoleDialog.title', { category: title })}</DialogTitle>
                  <DialogDescription>{t('rolesTab.addRoleDialog.description', { category: title })}</DialogDescription>
                </DialogHeader>
                <div className="space-y-2">
                  {availableRoles.map(role => (
                    <div key={role.id} className="flex items-center justify-between p-2 border rounded">
                      <div>
                        <div className="font-medium">{role.name}</div>
                        {role.description && <div className="text-sm text-muted-foreground">{role.description}</div>}
                      </div>
                      <Button
                        onClick={() => {
                          addRole(role.id)
                          setIsAddRoleOpen(false)
                        }}
                        disabled={isSaving}
                        size="sm"
                      >
                        {tCommon('actions.add')}
                      </Button>
                    </div>
                  ))}
                </div>
              </DialogContent>
            </Dialog>
          )}
        </div>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="h-8 w-8 animate-spin" />
        <span className="ml-2">{tCommon('loading')}</span>
      </div>
    )
  }

  if (!user) {
    return (
      <div className="p-8">
        <p>{t('rolesTab.userNotFound')}</p>
      </div>
    )
  }

  return (
    <div>
      <RoleCategory
        title={tRoles('systemRoles')}
        category="System"
        colorClass="bg-green-100 text-green-800 border-green-200"
      />

      <RoleCategory
        title={tRoles('dataRoles')}
        category="Data"
        colorClass="bg-blue-100 text-blue-800 border-blue-200"
      />

      <RoleCategory
        title={tRoles('governanceRoles')}
        category="Governance"
        colorClass="bg-purple-100 text-purple-800 border-purple-200"
      />

      <div className="fixed bottom-6 right-6 flex gap-4">
        <Button type="button" variant="secondary" onClick={handleCancel} disabled={!hasChanges || isSaving}>
          {tCommon('actions.cancel')}
        </Button>
        <Button type="button" onClick={handleSave} disabled={!hasChanges || isSaving}>
          {isSaving ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
          {tCommon('actions.submit')}
        </Button>
      </div>
    </div>
  )
}
