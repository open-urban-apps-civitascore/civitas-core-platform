'use client'

import { useTranslations } from 'next-intl'

import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { GenericAssignmentsList } from '@/components/access-management/GenericAssignmentsList'

interface AccessManagementTabProps {
  assignedGroups: GroupRoleAssignmentTable[]
  onAssignedGroupsChange: (groups: GroupRoleAssignmentTable[]) => void
  isReadOnly?: boolean
}

export const AccessManagementTab = (props: AccessManagementTabProps) => {
  const { assignedGroups, onAssignedGroupsChange, isReadOnly = false } = props
  const t = useTranslations('datapools.accessManagementTab')

  return (
    <GenericAssignmentsList
      assignedGroups={assignedGroups}
      onAssignedGroupsChange={onAssignedGroupsChange}
      isReadOnly={isReadOnly}
      tableTitle={t('title')}
      tableSubtitle={t('subtitle')}
      noDataSubtitle={t('noDataPage.subtitle')}
      firstBoxText={t('infoBoxes.firstBox')}
    />
  )
}
