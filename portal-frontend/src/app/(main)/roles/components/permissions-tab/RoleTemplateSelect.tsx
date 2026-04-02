import { useTranslations } from 'next-intl'
import { type JSX, useMemo } from 'react'

import { BasicSelect } from '@/components/basicSelect/BasicSelect'
import { Role } from '@/types/roles'

interface RoleTemplateSelectProps {
  setRoleTemplate: (roleId: string) => void
  templateRoles?: Role[]
  currentRoleId?: string
}

export const RoleTemplateSelect = (props: RoleTemplateSelectProps): JSX.Element => {
  const { setRoleTemplate, templateRoles, currentRoleId } = props
  const t = useTranslations('roles.permissionsTab')

  const rolesForSelect = useMemo((): { label: string; value: string }[] => {
    return (templateRoles ?? [])
      .filter(role => role.id !== currentRoleId)
      .map(role => ({
        label: role.name,
        value: role.id,
      }))
  }, [templateRoles, currentRoleId])

  return (
    <BasicSelect
      onValueChange={setRoleTemplate}
      options={rolesForSelect}
      placeholder={t('roleTemplate.placeholder')}
      triggerClassName="bg-white"
    />
  )
}
