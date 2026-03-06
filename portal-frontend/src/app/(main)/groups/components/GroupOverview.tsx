'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { FieldErrors, useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateGroup, useUpdateGroup } from '@/app/services/api/groups/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group, GroupBaseFormData, GroupBaseFormDataSchema, GroupTab } from '@/types/groups'
import { mapGroupApiToFormData, mapGroupFormToApiata } from '@/utils/groups'

import { BaseInfoTab } from './base-info-tab/BaseInfoTab'
import { RolesTab } from './roles-tab/RolesTab'
import { UsersTab } from './users-tab/UsersTab'

const tabValues: Record<GroupTab, Tab<GroupTab>> = {
  info: {
    value: 'info',
    label: 'groups.detailsTabs.info',
  },
  roles: {
    value: 'roles',
    label: 'groups.detailsTabs.roles',
  },
  users: {
    value: 'users',
    label: 'groups.detailsTabs.users',
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

  const { subTabValue, setSubTabValueParam } = useQueryParams()

  const t = useTranslations('groups')
  const tCommon = useTranslations('common')

  const tabs = Object.values(tabValues)
  const defaultTab = tabs[0].value
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(!isCreateMode && mode !== 'edit')

  const router = useRouter()
  const createGroup = useCreateGroup()
  const updateGroup = useUpdateGroup()

  const isLoading = createGroup.isPending || updateGroup.isPending || isNavigating

  const form = useForm<GroupBaseFormData>({
    resolver: zodResolver(GroupBaseFormDataSchema),
    defaultValues: mapGroupApiToFormData(initialGroupData),
  })

  const isFormDirty = form.formState.isDirty

  useEffect(() => {
    form.reset(mapGroupApiToFormData(initialGroupData))
  }, [initialGroupData, form])

  const handleGroupRequestError = (error: unknown, message: string) => {
    if ((error as AxiosError).response?.status == 409) {
      form.setError('name', { type: 'manual', message: 'groups.errors.groupNameExists' })
    }
    toast.error(t(message))
  }

  const handleCreateGroup = (formData: GroupBaseFormData) => {
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = mapGroupFormToApiata(formData)
    createGroup.mutate(createGroupData, {
      onSuccess: ({ data }) => {
        toast.success(tCommon('messages.createSuccess', { item: tCommon('items.group') }))
        setIsNavigating(true)
        router.push(`/groups/${data.id}?mode=edit`)
      },
      onError: (error: unknown) => handleGroupRequestError(error, 'errors.createError'),
    })
  }

  const handleUpdateGroup = (formData: GroupBaseFormData) => {
    updateGroup.mutate(mapGroupFormToApiata(formData), {
      onSuccess: ({ data }) => {
        toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.group') }))
        setInitialGroupData(data)
        setIsExitModalOpen(false)
      },
      onError: (error: unknown) => handleGroupRequestError(error, 'errors.updateError'),
    })
  }

  const handleExit = () => {
    form.reset()
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitAndSafe = () => {
    form.handleSubmit(handleSave)()
    setIsReadOnly(false)
  }

  const handleExitButtonClick = () => {
    if (isFormDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const handleSave = isCreateMode ? handleCreateGroup : handleUpdateGroup

  const handleValidationErrors = (errors: FieldErrors<GroupBaseFormData>) =>
    console.error('Validation errors: ', errors)

  const renderTabContent = () => {
    switch (subTabValue) {
      case tabValues.roles.value:
        return <RolesTab groupData={initialGroupData} />
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

  return (
    <PageContainer headerType="withSubTabsOrSubtitle">
      <PageHeader
        title={title}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: subTabValue || defaultTab,
          onTabChange: setSubTabValueParam,
        }}
        customElement={isReadOnly ? EditButton : SaveAndExitButtons}
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
