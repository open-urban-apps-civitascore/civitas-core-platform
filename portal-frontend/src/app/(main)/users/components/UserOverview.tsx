'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { RowSelectionState } from '@tanstack/react-table'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateUser, useUpdateUser } from '@/app/services/api/users/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { useError } from '@/hooks/use-error'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { User, UserFormData, UserFormSchema, UserTab } from '@/types/users'
import { isEmailConflictError } from '@/utils/errors'
import { pickDirtyValues } from '@/utils/form'
import { getHeaderAction } from '@/utils/headerAction'
import { mapUserToFormData } from '@/utils/users'

import { UserBasicInfoTab } from './basic-info-tab/UserBasicInfoTab'
import { GroupsTab } from './groups-tab/GroupsTab'
import { RolesTab } from './roles-tab/RolesTab'

const tabValues: Record<UserTab, Tab<UserTab>> = {
  userData: {
    label: 'users.detailsTabs.userData',
    value: 'userData',
  },
  groups: {
    label: 'users.detailsTabs.groups',
    value: 'groups',
  },
  roles: {
    label: 'users.detailsTabs.roles',
    value: 'roles',
  },
}

interface UserOverviewProps {
  userData: User
  title: string
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
  const queryClient = useQueryClient()
  const createUser = useCreateUser()
  const updateUser = useUpdateUser()
  const isLoading = createUser.isPending || updateUser.isPending
  const { setSubTabValueParam, subTabValue } = useQueryParams()
  const { handleUserEmailError } = useError()
  const { hasPermission } = usePermissions()
  const canUpdate = isCreateMode || hasPermission(PERMISSION_NAMES.USER_UPDATE)
  const onError = () => {
    toast.error(tCommon('errors.unexpectedError'))
  }

  const tabs: Tab<UserTab>[] = [
    tabValues.userData,
    ...(hasPermission(PERMISSION_NAMES.GROUP_READ) ? [tabValues.groups] : []),
    ...(hasPermission(PERMISSION_NAMES.ASSIGNMENT_READ) ? [tabValues.roles] : []),
  ]

  const defaultTab = tabValues.userData.value

  const handleSelectTab = (newTab: string) => {
    setSubTabValueParam(newTab)
  }

  const form = useForm<UserFormData>({
    resolver: zodResolver(UserFormSchema),
    defaultValues: mapUserToFormData(defaultUserData),
  })

  const watch = form.watch()
  const groupWatch = form.watch('groupIds')

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

  const handleAssignGroups = (groupSelection: RowSelectionState) => {
    const selectedgroupIds = Object.keys(groupSelection).filter(key => groupSelection[key])
    const updateGroupData = selectedgroupIds.concat(groupWatch || [])
    form.setValue('groupIds', updateGroupData, { shouldDirty: true })
  }

  const handleRemoveGroup = (id: string) => {
    const currentGroups = groupWatch.filter(group => group !== id)
    form.setValue('groupIds', currentGroups, { shouldDirty: true })
  }

  const handleExit = () => {
    if (isCreateMode) {
      router.push('/users')
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
        onError: error => {
          if (isEmailConflictError(error)) {
            handleUserEmailError(form, parsed.email)
          } else {
            toast.error(t('errors.creationError'))
          }
        },
      },
    )
  }

  const handleUpdateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)
    const dirtyFields = form.formState.dirtyFields
    const fieldsToUpdate = pickDirtyValues(parsed, dirtyFields)
    const updateData = {
      ...fieldsToUpdate,
      phone: !parsed.phone && dirtyFields.phone ? null : fieldsToUpdate.phone,
    }
    updateUser.mutate(
      { ...updateData, id: parsed.id },
      {
        onSuccess: ({ data }) => {
          ;[['groups']].forEach(queryKey => queryClient.invalidateQueries({ queryKey }))
          setDefaultUserData(data)
          if (isExitModalOpen) setIsExitModalOpen(false)
          router.refresh()
          toast.success(t('messages.updateSuccess'))
        },
        onError: error => {
          if (isEmailConflictError(error)) {
            handleUserEmailError(form, parsed.email)
          } else {
            toast.error(t('errors.updateError'))
          }
        },
      },
    )
  }

  const handleSave = isCreateMode ? form.handleSubmit(handleCreateUser) : form.handleSubmit(handleUpdateUser)

  const renderTabContent = () => {
    switch (subTabValue) {
      case tabValues.groups.value:
        return (
          <GroupsTab
            formValues={watch}
            isReadOnly={isReadOnly}
            onAssignGroups={handleAssignGroups}
            onRemoveGroup={handleRemoveGroup}
          />
        )
      case tabValues.roles.value:
        return <RolesTab userId={userData.id} isReadOnly={isReadOnly} />
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

  const headerCustomElement = getHeaderAction({
    isReadOnly,
    canUpdate,
    editButton: EditButton,
    saveExitButtons: SaveAndExitButtons,
  })

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: subTabValue || defaultTab,
          onTabChange: newTab => handleSelectTab(newTab),
        }}
        customElement={headerCustomElement}
      />
      {renderTabContent()}
      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleExit}
        onConfirm={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
