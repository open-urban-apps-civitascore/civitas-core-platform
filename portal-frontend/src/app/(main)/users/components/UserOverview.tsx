'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateUser, useUpdateUser } from '@/app/services/api/users/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ExitWarningModal } from '@/components/exit-warning-modal/ExitWarningModal'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { User, UserFormData, UserFormSchema, UserTab } from '@/types/users'
import { mapUserToFormData } from '@/utils/users'

import { UserBasicInfoTab } from './basic-info-tab/UserBasicInfoTab'
import { GroupsTab } from './groups-tab/GroupsTab'
import { RolesTab } from './roles-tab/RolesTab'

interface UserOverviewProps {
  title: string
  userData: User
  isCreateMode?: boolean
  testId?: string
}

export const UserOverview = (props: UserOverviewProps) => {
  const { title, userData, isCreateMode = false, testId } = props
  const params = useSearchParams()
  const mode = params.get('mode')
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const [defaultUserData, setDefaultUserData] = useState(userData)
  const [isReadOnly, setIsReadOnly] = useState(isCreateMode ? false : mode !== 'edit')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const createUser = useCreateUser()
  const updateUser = useUpdateUser()
  const isLoading = createUser.isPending || updateUser.isPending
  const { setSubTabValueParam, subTabValue } = useQueryParams()
  const onError = () => {
    toast.error(tCommon('errors.unexpectedError'))
  }

  const tabValues: Record<UserTab, Tab<UserTab>> = {
    userData: {
      label: t('detailsTabs.userData'),
      value: 'userData',
    },
    groups: {
      label: t('detailsTabs.groups'),
      value: 'groups',
    },
    roles: {
      label: t('detailsTabs.roles'),
      value: 'roles',
    },
  }
  const tabs: Tab<UserTab>[] = [tabValues.userData, tabValues.groups, tabValues.roles]

  const defaultTab = tabValues.userData.value

  const handleSelectTab = (newTab: string) => {
    setSubTabValueParam(newTab)
  }

  const form = useForm<UserFormData>({
    resolver: zodResolver(UserFormSchema),
    defaultValues: mapUserToFormData(defaultUserData),
  })

  const watch = form.watch()

  const isFormDirty = useMemo(() => {
    const dirtyFields = form.formState.dirtyFields
    const isPhoneFieldDirty =
      dirtyFields.phone && form.getValues('phone')?.replace(/\s+/g, '') !== defaultUserData?.phone?.replace(/\s+/g, '')
    const isNonPhoneFieldDirty = Object.keys(dirtyFields).find(field => field !== 'phone')
    return isNonPhoneFieldDirty || isPhoneFieldDirty
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watch, form, defaultUserData?.phone])

  useEffect(() => {
    form.reset(mapUserToFormData(defaultUserData))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultUserData])

  const handleExit = () => {
    form.reset()
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (isFormDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const handleCreateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)
    const mappedData = { ...parsed, groups: defaultUserData?.groups || [] }
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createUserData } = mappedData
    createUser.mutate(
      { ...createUserData, phone: createUserData.phone || null },
      {
        onSuccess: ({ data }) => {
          toast.success(t('messages.createSuccess'))
          router.push(`/users/${data.id}?mode=edit`)
        },
        onError,
      },
    )
  }

  const handleUpdateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)
    const updateData = { ...parsed, phone: !parsed.phone && defaultUserData.phone ? null : parsed.phone }
    updateUser.mutate(updateData, {
      onSuccess: ({ data }) => {
        setDefaultUserData(data)
        if (isExitModalOpen) setIsExitModalOpen(false)
        router.refresh()
        toast.success(t('messages.updateSuccess'))
      },
      onError,
    })
  }

  const handleSave = isCreateMode ? form.handleSubmit(handleCreateUser) : form.handleSubmit(handleUpdateUser)

  const renderTabContent = () => {
    switch (subTabValue) {
      case tabValues.groups.value:
        return <GroupsTab userId={userData.id} />
      case tabValues.roles.value:
        return <RolesTab groupIds={userData.groups} />
      case tabValues.userData.value:
      default:
        return <UserBasicInfoTab userData={userData} form={form} isReadOnly={isReadOnly} isLoading={isLoading} />
    }
  }

  const isSaveButtonDisabled = !isFormDirty || isLoading
  const isCancelButtonDisabled = isLoading

  const SaveAndExitButtons = (
    <ActionButtons
      confirmButtonType="button"
      onCancelClick={handleExitButtonClick}
      onConfirmClick={handleSave}
      isConfirmButtonDisabled={isSaveButtonDisabled}
      isCancelButtonDisabled={isCancelButtonDisabled}
      cancelButtonTitle={tCommon('actions.exit')}
      hasCard={false}
      className="px-6 py-0"
      wrapperClassname="w-auto"
    />
  )

  const EditButton = (
    <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)}>
      {tCommon('actions.edit')}
    </Button>
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        segmentedControlBarSectionProps={{
          tabs: tabs,
          selectedTab: subTabValue || defaultTab,
          onTabChange: newTab => handleSelectTab(newTab),
        }}
        customElement={isReadOnly ? EditButton : SaveAndExitButtons}
      />
      {renderTabContent()}
      <ExitWarningModal
        isOpen={isExitModalOpen}
        onClose={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onSave={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
