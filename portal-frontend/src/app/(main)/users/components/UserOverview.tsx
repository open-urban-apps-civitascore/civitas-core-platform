'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateUser, useUpdateUser } from '@/app/services/api/users/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { ExitWarningModal } from '@/components/exit-warning-modal/ExitWarningModal'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { User, UserFormData, UserFormSchema } from '@/types/users'
import { mapUserToFormData } from '@/utils/users'

import { UserBasicInfoTab } from './basic-info-tab/UserBasicInfoTab'
import { GroupsTab } from './groups-tab/GroupsTab'
import { RolesTab } from './roles-tab/RolesTab'

interface UserDetailsProps {
  title: string
  userData: User | null
  isEditMode?: boolean
  testId?: string
}

export const UserDetails = (props: UserDetailsProps) => {
  const { title, userData, isEditMode = false, testId } = props
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const [defaultUserData, setDefaultUserData] = useState(userData)
  const [isReadOnly, setIsReadOnly] = useState(isEditMode)
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const createUser = useCreateUser()
  const updateUser = useUpdateUser()
  const isLoading = createUser.isPending || updateUser.isPending

  const { setSubTabValueParam, subTabValue } = useQueryParams()
  const onError = () => {
    toast.error(tCommon('errors.unexpectedError'))
  }

  const tabValues: Record<'userData' | 'roles' | 'groups', Tab> = {
    userData: {
      label: t('detailsTabs.userData'),
      value: 'userDetails',
      isActive: true,
    },
    groups: {
      label: t('detailsTabs.groups'),
      value: 'userGroups',
      isActive: true,
    },
    roles: {
      label: t('detailsTabs.roles'),
      value: 'roles',
      isActive: true,
    },
  }
  const tabs: Tab[] = [tabValues.userData, tabValues.groups, tabValues.roles]

  const defaultTab = tabValues.userData.value

  const handleSelectTab = (newTab: string) => {
    console.log('handleSelectTab', newTab)
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
    const mappedData: User = { ...parsed, groups: defaultUserData?.groups || [] }
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createUserData } = mappedData
    createUser.mutate(createUserData, {
      onSuccess: ({ data }) => {
        toast.success(t('messages.createSuccess'))
        router.push(`/users/${data.id}`)
      },
      onError,
    })
  }

  const handleUpdateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)
    updateUser.mutate(parsed, {
      onSuccess: ({ data }) => {
        setDefaultUserData(data)
        setIsReadOnly(true)
        if (isExitModalOpen) setIsExitModalOpen(false)
        toast.success(t('messages.updateSuccess'))
      },
      onError,
    })
  }

  const handleSave = isEditMode ? form.handleSubmit(handleUpdateUser) : form.handleSubmit(handleCreateUser)

  let Content = <ContentCard>No data</ContentCard>
  if (userData) {
    switch (subTabValue) {
      case tabValues.userData.value:
      case '':
        Content = <UserBasicInfoTab userData={userData} form={form} isReadOnly={isReadOnly} isLoading={isLoading} />
        break
      case tabValues.groups.value:
        Content = <GroupsTab userId={userData.id} />
        break
      case tabValues.roles.value:
        Content = <RolesTab groupIds={userData.groups} />
        break
      default:
        Content = <ContentCard>No data</ContentCard>
        break
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
    <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)} className="mx-6">
      {tCommon('actions.edit')}
    </Button>
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        subTabs={{ tabs: tabs, selectedTab: subTabValue || defaultTab, onClick: newTab => handleSelectTab(newTab) }}
        customElement={isReadOnly ? EditButton : SaveAndExitButtons}
      />
      {userData ? Content : <NoDataPage title={t('notFound')} />}
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
