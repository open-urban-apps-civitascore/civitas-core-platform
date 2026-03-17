'use client'

import { useTranslations } from 'next-intl'

import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { GenericAssignmentsList } from '@/components/access-management/GenericAssignmentsList'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

interface AccessManagementTabProps {
  assignedGroups: GroupRoleAssignmentTable[]
  onAssignedGroupsChange: (groups: GroupRoleAssignmentTable[]) => void
  groups: Group[]
  roles: Role[]
  isReadOnly?: boolean
}

export const AccessManagementTab = (props: AccessManagementTabProps) => {
  const { assignedGroups, onAssignedGroupsChange, groups, roles, isReadOnly = false } = props
  const t = useTranslations('datastructures.accessManagementTab')

  return (
    <GenericAssignmentsList
      assignedGroups={assignedGroups}
      onAssignedGroupsChange={onAssignedGroupsChange}
      groups={groups}
      roles={roles}
      isReadOnly={isReadOnly}
      firstBoxText={t('infoBoxes.firstBox')}
    />
  )
}
