'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FormEvent, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateGroup, useReplaceGroupAssignments, useUpdateGroup } from '@/app/services/api/groups/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useError } from '@/hooks/use-error'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Group, GroupBaseFormData, GroupBaseFormDataSchema, GroupTab } from '@/types/groups'
import { Role } from '@/types/roles'
import { isNameConflictError } from '@/utils/errors'
import { mapGroupApiToFormData, mapGroupFormToApiData } from '@/utils/groups'
import { getHeaderAction } from '@/utils/headerAction'

import { BaseInfoTab } from './base-info-tab/BaseInfoTab'
import { RolesTab } from './roles-tab/RolesTab'
import { UsersTab } from './users-tab/UsersTab'

const tabValues: Record<GroupTab, Tab<GroupTab>> = {
  info: {
    value: 'info',
    label: 'groups.detailsTabs.info',
  },
  users: {
    value: 'users',
    label: 'groups.detailsTabs.users',
  },
  roles: {
    value: 'roles',
    label: 'groups.detailsTabs.roles',
  },
}

interface GroupDetailsProps {
  title: string
  groupData: Group
  isCreateMode: boolean
}
export const GroupOverview = (props: GroupDetailsProps) => {
  const { title, groupData, isCreateMode } = props
  const params = useSearchParams()
  const mode = params.get('mode')
  const [initialGroupData, setInitialGroupData] = useState(groupData)
  const [isNavigating, setIsNavigating] = useState(false)
  const [pendingRoles, setPendingRoles] = useState<Role[]>([])

  const { subTabValue, setSubTabValueParam } = useQueryParams()

  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const { hasPermission } = usePermissions()
  const { handleFormValidationError } = useError()

  const tabs = [
    tabValues.info,
    ...(hasPermission(PERMISSION_NAMES.USER_READ) ? [tabValues.users] : []),
    tabValues.roles,
  ]
  const defaultTab = tabs[0].value
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(!isCreateMode && mode !== 'edit')
  const canUpdate = isCreateMode || hasPermission(PERMISSION_NAMES.GROUP_UPDATE)

  const router = useRouter()
  const { handleNameError } = useError()
  const createGroup = useCreateGroup()
  const updateGroup = useUpdateGroup()
  const replaceAssignments = useReplaceGroupAssignments()

  const isLoading = createGroup.isPending || updateGroup.isPending || replaceAssignments.isPending || isNavigating

  const form = useForm<GroupBaseFormData>({
    resolver: zodResolver(GroupBaseFormDataSchema),
    defaultValues: mapGroupApiToFormData(initialGroupData),
  })

  const isFormDirty = form.formState.isDirty

  useEffect(() => {
    form.reset(mapGroupApiToFormData(initialGroupData))
    setPendingRoles([])
  }, [initialGroupData, form])

  const handleCreateGroup = async (formData: GroupBaseFormData): Promise<boolean> => {
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = mapGroupFormToApiData(formData)
    try {
      const { data } = await createGroup.mutateAsync(createGroupData)

      const navigateToGroup = () => {
        setIsNavigating(true)
        router.push(`/groups/${data.id}?mode=edit`)
      }

      if (formData.assignments.length > 0) {
        try {
          await replaceAssignments.mutateAsync({ groupId: data.id, assignments: formData.assignments })
          toast.success(tCommon('messages.createSuccess', { item: tCommon('items.group') }))
          navigateToGroup()
        } catch (error: unknown) {
          toast.error(t('errors.assignmentSaveError'))
          console.error('Failed to save assignments', error)
          navigateToGroup()
        }
      } else {
        toast.success(tCommon('messages.createSuccess', { item: tCommon('items.group') }))
        navigateToGroup()
      }

      return true
    } catch (error: unknown) {
      if (isNameConflictError(error)) {
        handleNameError(form, form.getValues('name'))
      } else {
        toast.error(t('errors.createError'))
      }
      return false
    }
  }

  const handleUpdateGroup = async (formData: GroupBaseFormData): Promise<boolean> => {
    try {
      const { data } = await updateGroup.mutateAsync(mapGroupFormToApiData(formData))
      try {
        const { data: assignmentData } = await replaceAssignments.mutateAsync({
          groupId: data.id,
          assignments: formData.assignments,
        })
        toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.group') }))
        setInitialGroupData(assignmentData)
        setPendingRoles([])
        setIsExitModalOpen(false)
        return true
      } catch (error) {
        toast.error(t('errors.updateError'))
        setInitialGroupData(data)
        console.error('Failed to save assignments', error)
        return false
      }
    } catch (error: unknown) {
      if (isNameConflictError(error)) {
        handleNameError(form, form.getValues('name'))
      } else {
        toast.error(t('errors.updateError'))
      }
      return false
    }
  }

  const handleExit = () => {
    if (isCreateMode) {
      router.push('/groups')
      return
    }
    form.reset()
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (isFormDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const handleSave = async () => {
    let isSaved = false

    await form.handleSubmit(
      async formData => {
        isSaved = isCreateMode ? await handleCreateGroup(formData) : await handleUpdateGroup(formData)
      },
      errors => {
        handleFormValidationError(errors)
        isSaved = false
      },
    )()

    return isSaved
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void handleSave()
  }

  useRegisterUnsavedChanges(isFormDirty, handleSave)

  const renderTabContent = () => {
    switch (subTabValue) {
      case tabValues.roles.value:
        return (
          <RolesTab
            form={form}
            groupData={initialGroupData}
            isReadOnly={isReadOnly}
            pendingRoles={pendingRoles}
            setPendingRoles={setPendingRoles}
          />
        )
      case tabValues.users.value:
        return (
          <UsersTab
            form={form}
            originalUsers={initialGroupData.members?.map(member => member.id) || []}
            isReadOnly={isReadOnly}
            isUpdatingGroup={updateGroup.isPending}
          />
        )
      case tabValues.info.value:
      default:
        return <BaseInfoTab form={form} initialContactUser={initialGroupData.contactUser} isReadOnly={isReadOnly} />
    }
  }

  const isConfirmButtonDisabled = isLoading || isReadOnly || !isFormDirty

  const SaveAndExitButtons = (
    <ActionButtons
      formId="groupEditForm"
      confirmButtonType="submit"
      onCancelClick={handleExitButtonClick}
      isConfirmButtonDisabled={isConfirmButtonDisabled}
      isCancelButtonDisabled={isLoading}
      cancelButtonTitle={tCommon('actions.exit')}
      hasCard={false}
      wrapperClassname="w-auto"
    />
  )

  const EditButton = (
    <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)}>
      {tCommon('actions.edit')}
    </Button>
  )

  const headerCustomElement = getHeaderAction({
    isReadOnly,
    canUpdate,
    editButton: EditButton,
    saveExitButtons: SaveAndExitButtons,
  })

  return (
    <PageContainer headerType="withSubTabsOrSubtitle">
      <PageHeader
        title={title}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: subTabValue || defaultTab,
          onTabChange: setSubTabValueParam,
        }}
        customElement={headerCustomElement}
      />
      <PageBackground className="flex flex-col" hasBackground={!isReadOnly}>
        <Form {...form}>
          <form id="groupEditForm" onSubmit={handleSubmit} className="flex flex-col justify-between h-full">
            {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>
      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
