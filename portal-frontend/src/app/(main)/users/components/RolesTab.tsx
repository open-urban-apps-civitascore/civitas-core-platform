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

interface Role {
  id: string
  name: string
  description: string
  type: 'System' | 'Data' | 'Governance'
}

interface User {
  id: string
  roles: string[]
  [key: string]: object | string | string[] | null | undefined
}

interface RolesTabProps {
  userId: string
}

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

export const RolesTab = ({ userId }: RolesTabProps) => {
  const t = useTranslations('users')
  const [user, setUser] = useState<User | null>(null)
  const [allRoles, setAllRoles] = useState<Role[]>([])
  const [isLoading, setLoading] = useState(true)
  const [savingRoleId, setSavingRoleId] = useState<string | null>(null)
  const [isAddRoleOpen, setIsAddRoleOpen] = useState(false)
  const [selectedCategory, setSelectedCategory] = useState<'System' | 'Data' | 'Governance'>('System')

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
        setAllRoles(rolesData)
      } catch (error) {
        console.error('Error fetching data:', error)
      } finally {
        setLoading(false)
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
  const addRole = async (roleId: string) => {
    if (!user) return

    setSavingRoleId(roleId)
    const updatedRoles = [...user.roles, roleId]

    // Optimistic update
    setUser({ ...user, roles: updatedRoles })

    try {
      const response = await fetch(`${URL}/users/${userId}`, {
        method: 'PATCH',
        headers: {
          ['Content-Type']: 'application/json',
        },
        body: JSON.stringify({ roles: updatedRoles }),
      })

      if (!response.ok) {
        throw new Error('Failed to add role')
      }
    } catch (error) {
      console.error('Error adding role:', error)
      // Rollback on error
      setUser({ ...user, roles: user.roles })
    } finally {
      setSavingRoleId(null)
    }
  }

  // Remove role from user
  const removeRole = async (roleId: string) => {
    if (!user) return

    setSavingRoleId(roleId)
    const updatedRoles = user.roles.filter(id => id !== roleId)

    // Optimistic update
    setUser({ ...user, roles: updatedRoles })

    try {
      const response = await fetch(`${URL}/users/${userId}`, {
        method: 'PATCH',
        headers: {
          ['Content-Type']: 'application/json',
        },
        body: JSON.stringify({ roles: updatedRoles }),
      })

      if (!response.ok) {
        throw new Error('Failed to remove role')
      }
    } catch (error) {
      console.error('Error removing role:', error)
      // Rollback on error
      setUser({ ...user, roles: user.roles })
    } finally {
      setSavingRoleId(null)
    }
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
                disabled={savingRoleId === role.id}
              >
                {savingRoleId === role.id ? <Loader2 className="h-3 w-3 animate-spin" /> : <X className="h-3 w-3" />}
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
                        disabled={savingRoleId === role.id}
                        size="sm"
                      >
                        {savingRoleId === role.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          t('rolesTab.addRoleDialog.addButton')
                        )}
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
        <span className="ml-2">{t('rolesTab.loading')}</span>
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
    <div className="p-6">
      <RoleCategory
        title={t('rolesTab.categories.systemRoles')}
        category="System"
        colorClass="bg-green-100 text-green-800 border-green-200"
      />

      <RoleCategory
        title={t('rolesTab.categories.dataRoles')}
        category="Data"
        colorClass="bg-blue-100 text-blue-800 border-blue-200"
      />

      <RoleCategory
        title={t('rolesTab.categories.governanceRoles')}
        category="Governance"
        colorClass="bg-purple-100 text-purple-800 border-purple-200"
      />
    </div>
  )
}
