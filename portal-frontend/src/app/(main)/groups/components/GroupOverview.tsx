'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { FieldErrors, useForm } from 'react-hook-form'
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

  const handleValidationErrors = (errors: FieldErrors<GroupBaseFormData>) => {
    console.error('Validation errors: ', errors)
    toast.error(tCommon('errors.formInvalid'))
  }

  const handleGroupRequestError = (error: AxiosError, defaultMessage: string) => {
    if (isNameConflictError(error)) handleNameError(form, form.getValues('name'))
    else toast.error(defaultMessage)
  }

  const handleCreateGroup = (formData: GroupBaseFormData) => {
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = mapGroupFormToApiData(formData)
    createGroup.mutate(createGroupData, {
      onSuccess: ({ data }) => {
        const navigateToGroup = () => {
          setIsNavigating(true)
          router.push(`/groups/${data.id}?mode=edit`)
        }

        if (formData.assignments.length > 0) {
          replaceAssignments.mutate(
            { groupId: data.id, assignments: formData.assignments },
            {
              onSuccess: () => {
                toast.success(tCommon('messages.createSuccess', { item: tCommon('items.group') }))
                navigateToGroup()
              },
              onError: (error: unknown) => {
                toast.error(t('errors.assignmentSaveError'))
                console.error('Failed to save assignments', error)
                navigateToGroup()
              },
            },
          )
        } else {
          toast.success(tCommon('messages.createSuccess', { item: tCommon('items.group') }))
          navigateToGroup()
        }
      },
      onError: error => handleGroupRequestError(error, t('errors.createError')),
    })
  }

  const handleUpdateGroup = (formData: GroupBaseFormData) => {
    updateGroup.mutate(mapGroupFormToApiData(formData), {
      onSuccess: ({ data }) => {
        replaceAssignments.mutate(
          { groupId: data.id, assignments: formData.assignments },
          {
            onSuccess: ({ data: assignmentData }) => {
              toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.group') }))
              setInitialGroupData(assignmentData)
              setPendingRoles([])
              setIsExitModalOpen(false)
            },
            onError: error => {
              toast.error(t('errors.updateError'))
              setInitialGroupData(data)
              console.error('Failed to save assignments', error)
            },
          },
        )
      },
      onError: error => handleGroupRequestError(error, t('errors.updateError')),
    })
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

  const handleExitAndSafe = () => {
    form.handleSubmit(handleSave)()
  }

  const handleExitButtonClick = () => {
    if (isFormDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const handleSave = isCreateMode ? handleCreateGroup : handleUpdateGroup

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
          <form
            id="groupEditForm"
            onSubmit={form.handleSubmit(handleSave, handleValidationErrors)}
            className="flex flex-col justify-between h-full"
          >
            {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>
      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={handleExitAndSafe}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
