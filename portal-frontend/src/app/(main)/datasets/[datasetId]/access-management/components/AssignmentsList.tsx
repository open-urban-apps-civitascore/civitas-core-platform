'use client'

import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { GenericAssignmentsList } from '@/components/access-management/GenericAssignmentsList'
import { AssignmentScopedInput } from '@/types/assignments'

type AssignmentsListProps = {
  datasetId: string
  initialAssignments: GroupRoleAssignmentTable[]
}

export const AssignmentsList = (props: AssignmentsListProps) => {
  const { datasetId, initialAssignments } = props
  const t = useTranslations('accessManagement')
  const router = useRouter()

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
      onPatchEntity={handlePatchEntity}
      title={t('title')}
      subtitle={t('subtitle')}
      testId="accessManagement"
      hasSecondBox
      onExit={() => router.push(`/datasets/${datasetId}`)}
    />
  )
}
