'use client'

import { Plus, X } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
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
import { BaseRole, RoleType } from '@/types/roles'

interface RolesCategoryProps {
  title: string
  groupRoles: string[]
  category: RoleType
  allRoles: BaseRole[]
  onAddRole: (id: string) => void
  onRemoveRole: (id: string) => void
}

export const RoleCategory = (props: RolesCategoryProps) => {
  const { category, title, groupRoles, allRoles, onAddRole, onRemoveRole } = props
  const t = useTranslations('groups')
  const [isSaving, _setIsSaving] = useState(false)
  const [isAddRoleOpen, setIsAddRoleOpen] = useState(false)

  const assignedRoles = allRoles.filter(role => groupRoles.includes(role.id) && role.type === category)
  const availableRoles = allRoles.filter(role => !groupRoles.includes(role.id) && role.type === category)

  return (
    <DetailsFieldContainer className="min-h-21 flex itme-center ">
      <div className="flex items-center">
        <h3 className="w-[228px] text-sm">{title}</h3>
        <div className="flex flex-wrap gap-2 items-center">
          {assignedRoles.map(role => (
            <Badge key={role.id} variant="secondary" className="relative group p-2 h-9">
              <span>{role.name}</span>
              <Button
                type="button"
                variant="ghost"
                size="normal"
                className="p-0 ml-2 opacity-70 hover:opacity-100"
                onClick={() => onRemoveRole(role.id)}
              >
                <X className="h-3 w-3" />
              </Button>
            </Badge>
          ))}

          {availableRoles.length > 0 && (
            <Dialog
              open={isAddRoleOpen}
              onOpenChange={open => {
                setIsAddRoleOpen(open)
              }}
            >
              <DialogTrigger asChild>
                <Button variant="ghost" className="h-6 w-6 p-0 rounded-sm">
                  <Plus
                    className="h-4 w-4"
                    color="var(--muted-foreground)"
                    style={{ height: '--spacing(6)', width: '--spacing(6)' }}
                  />
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>{t('roles.addRoleDialog.title', { category: title })}</DialogTitle>
                  <DialogDescription>{t('roles.addRoleDialog.description', { category: title })}</DialogDescription>
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
                          onAddRole(role.id)
                          setIsAddRoleOpen(false)
                        }}
                        disabled={isSaving}
                        size="sm"
                      >
                        {t('roles.addRoleDialog.addButton')}
                      </Button>
                    </div>
                  ))}
                </div>
              </DialogContent>
            </Dialog>
          )}
        </div>
      </div>
    </DetailsFieldContainer>
  )
}
