'use client'

import { useTranslations } from 'next-intl'

import { usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { GenericAssignmentsList } from '@/components/access-management/GenericAssignmentsList'
import { AssignmentScopedInput } from '@/types/assignments'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'

type AssignmentsListProps = {
  datasetId: string
  initialAssignments: GroupRoleAssignmentTable[]
  groups: Group[]
  roles: Role[]
}

export const AssignmentsList = (props: AssignmentsListProps) => {
  const { datasetId, initialAssignments, groups, roles } = props
  const t = useTranslations('accessManagement')

  const { mutateAsync: patchDataset } = usePatchDataset()

  const handlePatchEntity = async (id: string, assignments: AssignmentScopedInput[]) => {
    await patchDataset({
      id,
      assignments,
    })
  }

  return (
    <GenericAssignmentsList
      entityId={datasetId}
      initialAssignments={initialAssignments}
      groups={groups}
      roles={roles}
      onPatchEntity={handlePatchEntity}
      title={t('title')}
      subtitle={t('subtitle')}
      testId="accessManagement"
      hasSecondBox
    />
  )
}
