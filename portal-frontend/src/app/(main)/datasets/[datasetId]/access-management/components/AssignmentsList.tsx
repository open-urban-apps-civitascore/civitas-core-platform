'use client'

import { AxiosError } from 'axios'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import { toast } from 'sonner'

import {
  usePatchDataset,
  useUpdateReadyDatasetMeta,
  useUpdateReleasedDatasetMeta,
} from '@/app/services/api/datasets/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { GenericAssignmentsList } from '@/components/access-management/GenericAssignmentsList'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useDatasetPermissions } from '@/hooks/use-dataset-permissions'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { Dataset } from '@/types/datasets'
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'

type AssignmentsListProps = {
  dataset: Dataset
  initialAssignments: GroupRoleAssignmentTable[]
}

export const AssignmentsList = (props: AssignmentsListProps) => {
  const { dataset, initialAssignments } = props
  const t = useTranslations('accessManagement')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const searchParams = useSearchParams()

  const { isDraft, isReady, canEditMetadata, canReadDatastructures } = useDatasetPermissions(dataset)
  const canEdit = canEditMetadata && canReadDatastructures

  const [assignedGroups, setAssignedGroups] = useState<GroupRoleAssignmentTable[]>(initialAssignments)
  const [isReadOnly, setIsReadOnly] = useState(searchParams.get('mode') !== 'edit' || !canEdit)
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(false)

  const { mutateAsync: patchDataset } = usePatchDataset()
  const { mutateAsync: updateReadyMeta } = useUpdateReadyDatasetMeta()
  const { mutateAsync: updateReleasedMeta } = useUpdateReleasedDatasetMeta()

  const hasChanges = hasAssignmentChanges(assignedGroups, initialAssignments)

  const handleExit = () => router.push(`/datasets/${dataset.id}`)

  const onSubmit = async (): Promise<boolean> => {
    setIsLoading(true)
    const areAssignmentsInvalid = assignedGroups.some(group => group.assignedRoles.length === 0)
    try {
      const assignments = mapGroupRoleAssignmentsToApiPayload(assignedGroups)
      const realeasedPayload = {
        id: dataset.id,
        name: dataset.name,
        description: dataset.description,
        openDataAccess: dataset.openDataAccess,
        assignments,
      }
      if (isDraft) {
        await patchDataset({ id: dataset.id, assignments })
      } else if (isReady) {
        // Ready datasets go through the ready/meta PATCH endpoint
        await updateReadyMeta(realeasedPayload)
      } else {
        // Released datasets go through the released/meta PATCH endpoint
        await updateReleasedMeta(realeasedPayload)
      }
      toast.success(t('messages.updateSuccess'))
      if (areAssignmentsInvalid) {
        toast.warning(t('messages.groupsWithoutRoles'))
      }
      setIsExitModalOpen(false)
      setIsReadOnly(true)
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
      router.refresh()
      return true
    } catch (error) {
      console.error('Error updating dataset assignments:', error)
      const axiosError = error as AxiosError
      if (isDraft && axiosError?.response?.status === 400) {
        toast.error(t('messages.updateErrorNotDraft'))
      } else if (!isDraft && axiosError?.response?.status === 409) {
        toast.error(t('messages.updateErrorSagaInFlight'))
      } else {
        toast.error(tCommon('errors.unexpectedError'))
      }
      setAssignedGroups(initialAssignments)
      setIsReadOnly(true)
      return false
    } finally {
      setIsLoading(false)
    }
  }

  useRegisterUnsavedChanges(hasChanges, onSubmit)

  const handleCancel = () => {
    if (hasChanges) {
      setIsExitModalOpen(true)
    } else {
      handleExit()
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    handleExit()
  }

  const handleSaveAndExit = async () => {
    const isSuccess = await onSubmit()
    if (isSuccess) handleExit()
  }

  const editControls = (
    <PageEditControls
      isReadOnly={isReadOnly}
      canEdit={canEdit}
      onEditClick={() => setIsReadOnly(false)}
      confirmButtonType="button"
      onConfirmClick={onSubmit}
      onCancelClick={handleCancel}
      isConfirmButtonDisabled={!hasChanges || isLoading}
      isCancelButtonDisabled={isLoading}
      confirmButtonTitle={tCommon('actions.submit')}
      cancelButtonTitle={tCommon('actions.exit')}
      hasCard={false}
      wrapperClassname="w-auto"
    />
  )

  return (
    <PageContainer testId="accessManagement" headerType="withSubTabsOrSubtitle" className="overflow-auto">
      <PageHeader title={t('title')} subtitle={t('subtitle')} customElement={editControls} />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        {isLoading ? (
          <LoadingSpinner className="h-full" />
        ) : (
          <GenericAssignmentsList
            testId="accessManagementList"
            assignedGroups={assignedGroups}
            onAssignedGroupsChange={setAssignedGroups}
            isReadOnly={isReadOnly}
            isLoading={isLoading}
            hasSecondBox
          />
        )}
      </PageBackground>
      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
