import { useTranslations } from 'next-intl'
import { type JSX, useMemo } from 'react'

import { BasicSelect } from '@/components/basicSelect/BasicSelect'
import { Role } from '@/types/roles'

type Props = {
  setRoleTemplate: (roleId: string) => void
  allRoles: Role[]
}

export const RoleTemplateSelect = (props: Props): JSX.Element => {
  const { setRoleTemplate, allRoles } = props
  const t = useTranslations('roles.permissionsTab')

  const rolesForSelect = useMemo((): { label: string; value: string }[] => {
    return allRoles.map(role => ({
      label: role.name,
      value: role.id,
    }))
  }, [allRoles])

  return (
    <BasicSelect onValueChange={setRoleTemplate} options={rolesForSelect} placeholder={t('roleTemplate.placeholder')} />
  )
}
