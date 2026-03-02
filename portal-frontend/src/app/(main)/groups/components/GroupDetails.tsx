'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
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
import { useQueryParams } from '@/hooks/use-query-params'
import { Group, GroupBaseFormData, GroupBaseFormDataSchema, GroupTab } from '@/types/groups'
import { mapGroupApiToFormData } from '@/utils/groups'

import { BaseInfoTab } from './BaseInfoTab'
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
export const GroupDetails = (props: GroupDetailsProps) => {
  const { title, groupData, isCreateMode } = props
  const params = useSearchParams()
  const mode = params.get('mode')
  const [initialGroupData, setInitialGroupData] = useState(groupData)

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

  const isLoading = createGroup.isPending || updateGroup.isPending

  useEffect(() => {
    setInitialGroupData(initialGroupData)
  }, [initialGroupData])

  const form = useForm<GroupBaseFormData>({
    resolver: zodResolver(GroupBaseFormDataSchema),
    defaultValues: mapGroupApiToFormData(initialGroupData),
  })

  const isFormDirty = form.formState.isDirty

  useEffect(() => {
    form.reset(mapGroupApiToFormData(initialGroupData))
  }, [initialGroupData, form])

  const handleCreateGroup = (formData: GroupBaseFormData) => {
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createGroupData } = formData
    createGroup.mutate(createGroupData, {
      onSuccess: ({ data }) => {
        toast.success(tCommon('messages.createSuccess', { item: tCommon('items.group') }))
        router.push(`/groups/${data.id}?mode=edit`)
      },
      onError: () => toast.error(t('errors.createError')),
    })
  }

  const handleUpdateGroup = (formData: GroupBaseFormData) => {
    updateGroup.mutate(formData, {
      onSuccess: ({ data }) => {
        toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.group') }))
        setInitialGroupData(data)
        router.refresh()
        setIsExitModalOpen(false)
      },
      onError: () => toast.error(t('errors.updateError')),
    })
  }

  const handleExit = () => {
    form.reset()
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (isFormDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const handleSubmit = isCreateMode ? handleCreateGroup : handleUpdateGroup

  const renderTabContent = () => {
    switch (subTabValue) {
      case tabValues.roles.value:
        return <RolesTab groupData={groupData} />
      case tabValues.users.value:
        return <UsersTab groupData={groupData} />
      case tabValues.info.value:
      default:
        return (
          <BaseInfoTab
            form={form}
            initialContactUser={initialGroupData.contactUser}
            onSubmit={handleSubmit}
            isReadOnly={isReadOnly}
          />
        )
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
      <PageBackground className="flex flex-col">
        {' '}
        {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
      </PageBackground>
      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={form.handleSubmit(handleSubmit)}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
