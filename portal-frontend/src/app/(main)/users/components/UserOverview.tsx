'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { usePatchGroup } from '@/app/services/api/groups/clientRequests'
import { apiRequest } from '@/app/services/api/request/apiRequest'
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
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Group } from '@/types/groups'
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
  const { handleFormValidationError } = useError()
  const createUser = useCreateUser()
  const updateUser = useUpdateUser()
  const patchGroup = usePatchGroup()
  const isLoading = createUser.isPending || updateUser.isPending || patchGroup.isPending
  const { setSubTabValueParam, subTabValue } = useQueryParams()
  const { handleUserEmailError } = useError()
  const { hasPermission } = usePermissions()
  const canUpdateUser = isCreateMode || hasPermission(PERMISSION_NAMES.USER_UPDATE)
  const canUpdateGroups = hasPermission(PERMISSION_NAMES.GROUP_UPDATE)
  const canUpdate = canUpdateUser || canUpdateGroups

  const tabs: Tab<UserTab>[] = [
    tabValues.userData,
    ...(!isCreateMode && hasPermission(PERMISSION_NAMES.GROUP_READ) ? [tabValues.groups] : []),
    ...(hasPermission(PERMISSION_NAMES.ASSIGNMENT_READ) ? [tabValues.roles] : []),
  ]

  const disabledTabs = isCreateMode ? [tabValues.roles.value] : undefined
  const disabledTabTooltips = isCreateMode ? { [tabValues.roles.value]: t('rolesTab.disabledHint') } : undefined

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

  const handleAssignGroups = (selectedGroupIds: string[]) => {
    const updateGroupData = selectedGroupIds.concat(groupWatch || [])
    form.setValue('groupIds', updateGroupData, { shouldDirty: true })
  }

  const handleRemoveGroup = (id: string) => {
    const currentGroups = groupWatch.filter(group => group !== id)
    form.setValue('groupIds', currentGroups, { shouldDirty: true })
  }

  const saveGroupMemberships = useCallback(
    async (userId: string) => {
      const originalGroupIds = defaultUserData?.groups?.map(g => g.id) || []
      const currentGroupIds = form.getValues('groupIds')

      const addedGroupIds = currentGroupIds.filter(id => !originalGroupIds.includes(id))
      const removedGroupIds = originalGroupIds.filter(id => !currentGroupIds.includes(id))

      if (addedGroupIds.length === 0 && removedGroupIds.length === 0) return

      // Fetch all affected groups in parallel
      const [removedResponses, addedResponses] = await Promise.all([
        Promise.all(
          removedGroupIds.map(id =>
            apiRequest<Group>({ endpoint: `/groups/${id}`, method: 'GET', headers: { 'x-api-request': 'true' } }),
          ),
        ),
        Promise.all(
          addedGroupIds.map(id =>
            apiRequest<Group>({ endpoint: `/groups/${id}`, method: 'GET', headers: { 'x-api-request': 'true' } }),
          ),
        ),
      ])

      const groupPatches: Promise<unknown>[] = []

      removedGroupIds.forEach((groupId, i) => {
        const currentMemberIds = removedResponses[i].data.members?.map(m => m.id) || []
        groupPatches.push(
          patchGroup.mutateAsync({ id: groupId, memberIds: currentMemberIds.filter(id => id !== userId) }),
        )
      })

      addedGroupIds.forEach((groupId, i) => {
        const currentMemberIds = addedResponses[i].data.members?.map(m => m.id) || []
        if (!currentMemberIds.includes(userId)) {
          groupPatches.push(patchGroup.mutateAsync({ id: groupId, memberIds: [...currentMemberIds, userId] }))
        }
      })

      await Promise.all(groupPatches)
    },
    [defaultUserData, form, patchGroup],
  )

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

  const handleCreateUser = async (formData: UserFormData): Promise<boolean> => {
    const parsed = UserFormSchema.parse(formData)
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, groupIds, ...createUserData } = parsed
    try {
      const { data } = await createUser.mutateAsync({ ...createUserData, phone: createUserData.phone || null })
      toast.success(t('messages.createSuccess'))
      router.push(`/users/${data.id}?mode=edit`)
      return true
    } catch (error) {
      if (isEmailConflictError(error)) {
        handleUserEmailError(form, parsed.email)
      } else {
        toast.error(t('errors.creationError'))
      }
      return false
    }
  }

  const handleUpdateUser = async (formData: UserFormData): Promise<boolean> => {
    const parsed = UserFormSchema.parse(formData)
    const dirtyFields = form.formState.dirtyFields

    const { groupIds: _groupIds, ...dirtyUserFields } = pickDirtyValues(parsed, dirtyFields)
    const hasUserFieldChanges = Object.keys(dirtyUserFields).length > 0
    const hasGroupChanges = !!dirtyFields.groupIds

    // Guard: nothing actionable
    if (!hasUserFieldChanges && !(hasGroupChanges && canUpdateGroups)) return true

    try {
      // Save group membership changes via PATCH /groups/{id}
      if (hasGroupChanges && canUpdateGroups) {
        await saveGroupMemberships(parsed.id)
      }

      // Save user field changes via PATCH /users/{id}
      if (hasUserFieldChanges && canUpdateUser) {
        const updateData = {
          ...dirtyUserFields,
          phone: !parsed.phone && dirtyFields.phone ? null : dirtyUserFields.phone,
        }
        const { data } = await updateUser.mutateAsync({ ...updateData, id: parsed.id })
        setDefaultUserData(data)
      } else if (hasGroupChanges) {
        // Group-only save: fetch updated user to keep local state in sync
        const { data } = await apiRequest<User>({
          endpoint: `/users/${parsed.id}`,
          method: 'GET',
          headers: { 'x-api-request': 'true' },
        })
        setDefaultUserData(data)
      }

      router.refresh()
      toast.success(t('messages.updateSuccess'))
      if (isExitModalOpen) setIsExitModalOpen(false)
      return true
    } catch (error) {
      if (isEmailConflictError(error)) {
        handleUserEmailError(form, parsed.email)
      } else {
        toast.error(t('errors.updateError'))
      }
      return false
    } finally {
      queryClient.invalidateQueries({ queryKey: ['groups'] })
      queryClient.invalidateQueries({ queryKey: ['users'] })
    }
  }

  const handleSave = async () => {
    let isSaved = false

    await form.handleSubmit(
      async formData => {
        isSaved = isCreateMode ? await handleCreateUser(formData) : await handleUpdateUser(formData)
      },
      errors => {
        handleFormValidationError(errors)
        isSaved = false
      },
    )()

    return isSaved
  }

  useRegisterUnsavedChanges(form.formState.isDirty, handleSave)

  const renderTabContent = () => {
    switch (subTabValue) {
      case tabValues.groups.value:
        return (
          <GroupsTab
            userId={userData.id}
            formValues={watch}
            isReadOnly={isReadOnly || !canUpdateGroups}
            onAssignGroups={handleAssignGroups}
            onRemoveGroup={handleRemoveGroup}
          />
        )
      case tabValues.roles.value:
        return <RolesTab userId={userData.id} isReadOnly={isReadOnly} />
      case tabValues.userData.value:
      default:
        return (
          <UserBasicInfoTab
            userData={userData}
            form={form}
            isReadOnly={isReadOnly || !canUpdateUser}
            isLoading={isLoading}
          />
        )
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
          disabledTabs,
          disabledTabTooltips,
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
