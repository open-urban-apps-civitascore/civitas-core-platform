'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateDatapool, usePatchDatapool } from '@/app/services/api/datapools/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Datapool, DatapoolFormData, DatapoolFormSchema, DatapoolTab, DatapoolTabValues } from '@/types/datapools'
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'
import { pickDirtyValues } from '@/utils/form'

import { AccessManagementTab } from './access-management-tab/AccessManagementTab'
import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { DatasetTab } from './datasets/DatasetTab'
import { DatasourceTab } from './datasources/DatasourceTab'

interface DatapoolOverviewProps {
  datapool: Datapool
  initialAssignments?: GroupRoleAssignmentTable[]
  isCreateMode?: boolean
  title?: string
}

const allTabs: Tab<DatapoolTab>[] = [
  { value: 'basicInfo', label: 'datapools.overview.tabs.basicInfo' },
  { value: 'accessManagement', label: 'datapools.overview.tabs.accessManagement' },
  { value: 'datasets', label: 'datapools.overview.tabs.datasets' },
  { value: 'datasources', label: 'datapools.overview.tabs.datasources' },
]

const mapDatapoolToFormData = (datapool: Datapool): DatapoolFormData => ({
  id: datapool.id,
  name: datapool.name,
  description: datapool.description,
  contactPersonId: datapool.contactPerson?.id ?? null,
})

export const DatapoolOverview = (props: DatapoolOverviewProps) => {
  const { datapool, initialAssignments = [], isCreateMode = false, title } = props
  const t = useTranslations('datapools')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()
  const { subTabValue, setSubTabValueParam } = useQueryParams()
  const isReadOnly = !isCreateMode && searchParams.get('mode') !== 'edit'
  const [savedAssignments, setSavedAssignments] = useState<GroupRoleAssignmentTable[]>(initialAssignments)
  const [assignedGroups, setAssignedGroups] = useState<GroupRoleAssignmentTable[]>(initialAssignments)

  const { hasScopedPermission } = usePermissions()
  const canUpdate = hasScopedPermission(PERMISSION_NAMES.DATAPOOL_UPDATE, ASSIGNMENT_SCOPE_TYPES.DATAPOOL, datapool.id)

  const { mutateAsync: createDatapool, isPending: isCreating } = useCreateDatapool()
  const { mutate: updateDatapool, isPending: isUpdating } = usePatchDatapool()
  const isLoading = isCreating || isUpdating

  const updateMode = useCallback(
    (isEditing: boolean) => {
      const params = new URLSearchParams(searchParams.toString())
      if (isEditing) {
        params.set('mode', 'edit')
      } else {
        params.delete('mode')
      }
      const query = params.toString()
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
    },
    [pathname, searchParams, router],
  )

  const form = useForm<DatapoolFormData>({
    resolver: zodResolver(DatapoolFormSchema),
    mode: 'onChange',
    defaultValues: mapDatapoolToFormData(datapool),
  })

  const selectedTab = (subTabValue as DatapoolTab) || DatapoolTabValues.basicInfo
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const areAssignmentsDirty = useMemo(
    () => hasAssignmentChanges(assignedGroups, savedAssignments),
    [assignedGroups, savedAssignments],
  )

  const hasUnsavedChanges = form.formState.isDirty || areAssignmentsDirty

  const isBasicInfoTabCompleted = !!form.getValues().name && !!form.getValues().description
  const completedTabs = isBasicInfoTabCompleted ? (['basicInfo'] as DatapoolTab[]) : ([] as DatapoolTab[])

  const resetToInitialState = () => {
    form.reset(mapDatapoolToFormData(datapool), { keepDirty: false })
    setAssignedGroups(savedAssignments)
  }

  const handleCreateDatapool = async (formData: DatapoolFormData): Promise<boolean> => {
    try {
      const areAssignmentsInvalid = assignedGroups.some(group => group.assignedRoles.length === 0)
      const assignmentsPayload = mapGroupRoleAssignmentsToApiPayload(assignedGroups)

      const { data } = await createDatapool({
        name: formData.name,
        description: formData.description,
        contactPersonId: formData.contactPersonId ?? undefined,
        assignments: assignmentsPayload.length > 0 ? assignmentsPayload : undefined,
      })

      toast.success(tCommon('messages.createSuccess', { item: tCommon('items.datapool') }))
      if (areAssignmentsInvalid) {
        toast.error(t('errors.groupsWithoutRoles'))
      }
      router.push(`/datapools/${data.id}?mode=edit`)
      return true
    } catch {
      toast.error(t('errors.createError'))
      return false
    }
  }

  const handleUpdateDatapool = (formData: DatapoolFormData): Promise<boolean> => {
    const valuesToUpdate = pickDirtyValues(formData, form.formState.dirtyFields)
    const areAssignmentsInvalid = assignedGroups.some(group => group.assignedRoles.length === 0)
    const assignmentsPayload = mapGroupRoleAssignmentsToApiPayload(assignedGroups)
    const assignmentsPatch = areAssignmentsDirty
      ? { assignments: assignmentsPayload.length > 0 ? assignmentsPayload : undefined }
      : {}

    return new Promise(resolve => {
      updateDatapool(
        { id: datapool.id, ...valuesToUpdate, ...assignmentsPatch },
        {
          onSuccess: data => {
            toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.datapool') }))
            if (areAssignmentsInvalid) {
              toast.error(t('errors.groupsWithoutRoles'))
            }
            updateMode(false)
            router.refresh()
            form.reset(mapDatapoolToFormData(data.data), { keepDirty: false })
            const updatedAssignments = assignedGroups.filter(g => g.assignedRoles.length > 0)
            setAssignedGroups(updatedAssignments)
            setSavedAssignments(updatedAssignments)
            resolve(true)
          },
          onError: () => {
            toast.error(t('errors.updateError'))
            resolve(false)
          },
        },
      )
    })
  }

  const handleSave = async (): Promise<boolean> => {
    let isSaved = false

    await form.handleSubmit(async formData => {
      isSaved = isCreateMode ? await handleCreateDatapool(formData) : await handleUpdateDatapool(formData)
    })()

    return isSaved
  }

  useRegisterUnsavedChanges(hasUnsavedChanges, handleSave)

  const handleExit = async () => {
    if (hasUnsavedChanges) {
      const isValid = await form.trigger()
      if (isValid) {
        setIsExitModalOpen(true)
        return
      }
    }
    if (isCreateMode) {
      router.push('/datapools')
      return
    }
    resetToInitialState()
    updateMode(false)
  }

  const handleDiscardAndExit = () => {
    if (isCreateMode) {
      router.push('/datapools')
      return
    }
    setIsExitModalOpen(false)
    updateMode(false)
    resetToInitialState()
  }

  const handleSaveAndExit = async () => {
    const isSaved = await handleSave()
    if (!isSaved) return
    if (isCreateMode) {
      router.push('/datapools')
    } else {
      setIsExitModalOpen(false)
    }
  }

  const EditButton = canUpdate ? (
    <Button data-testid="editButton" type="button" onClick={() => updateMode(true)}>
      {tCommon('actions.edit')}
    </Button>
  ) : undefined

  const isConfirmButtonDisabled = isLoading || !form.formState.isValid || (!isCreateMode && !hasUnsavedChanges)

  const ActionButtonsElement = (
    <ActionButtons
      onCancelClick={handleExit}
      isConfirmButtonDisabled={isConfirmButtonDisabled}
      confirmButtonType="button"
      onConfirmClick={handleSave}
      hasCard={false}
      confirmButtonTitle={tCommon('actions.submit')}
      cancelButtonTitle={tCommon('actions.exit')}
    />
  )

  const renderTabContent = () => {
    switch (selectedTab) {
      case DatapoolTabValues.basicInfo:
        return <BasicInfoTab form={form} isReadOnly={isReadOnly} datapool={datapool} />
      case DatapoolTabValues.accessManagement:
        return (
          <AccessManagementTab
            assignedGroups={assignedGroups}
            onAssignedGroupsChange={setAssignedGroups}
            isReadOnly={isReadOnly}
          />
        )
      case DatapoolTabValues.datasets:
        return <DatasetTab datapoolId={datapool.id} isReadOnly={isReadOnly} isCreateMode={isCreateMode} />
      case DatapoolTabValues.datasources:
        return <DatasourceTab datapoolId={datapool.id} isCreateMode={isCreateMode} />
      default:
        return null
    }
  }

  return (
    <PageContainer testId="datapoolOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title ?? datapool.name}
        segmentedControlBarProps={{
          tabs: allTabs,
          selectedTab: selectedTab,
          onTabChange: newTab => setSubTabValueParam(newTab),
          completedTabs,
          tabsWithNoCompletionStatus: ['accessManagement', 'datasets', 'datasources'],
          hasCompletionStatus: true,
        }}
        customElement={isReadOnly && !isCreateMode ? EditButton : ActionButtonsElement}
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly || isCreateMode}>
        <Form {...form}>
          <form
            data-testid="datapoolOverviewForm"
            aria-label={`${tCommon('form')} ${t('form.title')}`}
            onSubmit={e => e.preventDefault()}
          >
            {isLoading ? <LoadingSpinner className="h-75" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        isLoading={false}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
      />
    </PageContainer>
  )
}
