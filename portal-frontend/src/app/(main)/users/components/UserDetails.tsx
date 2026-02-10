'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { useCreateUser, useUpdateUser } from '@/app/services/api/users/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/use-query-params'
import { User, UserFormData, UserFormSchema } from '@/types/users'
import { mapUserToFormData } from '@/utils/users'

import { GroupsTab } from './groups-tab/GroupsTab'
import { RolesTab } from './roles-tab/RolesTab'
import { UserForm } from './UserForm'

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
  const [isSaveButtonDisabled, setIsSaveButtonDisabled] = useState(true)
  const createUser = useCreateUser()
  const updateUser = useUpdateUser()
  const isLoading = createUser.isPending || updateUser.isPending

  const { setSubTabValueParam, subTabValue, getApiRequestParamsByUrl } = useQueryParams()

  const tabValues: Record<'userData' | 'roles' | 'groups' | 'account', Tab> = {
    userData: {
      label: t('detailsTabs.userData'),
      value: 'userDetails',
      isActive: true,
    },
    groups: {
      label: t('detailsTabs.groups'),
      value: 'userGroups',
      isActive: isEditMode,
    },
    roles: {
      label: t('detailsTabs.roles'),
      value: 'roles',
      isActive: isEditMode,
    },
    account: {
      label: t('detailsTabs.account'),
      value: 'account',
      isActive: isEditMode,
    },
  }
  const tabs: Tab[] = [tabValues.userData, tabValues.groups, tabValues.roles, tabValues.account]

  const defaultTab = tabValues.userData.value

  const isBlockedTab = useMemo(() => !isEditMode && subTabValue !== defaultTab, [isEditMode, subTabValue, defaultTab])

  useEffect(() => {
    if (!subTabValue || isBlockedTab) {
      setSubTabValueParam(defaultTab)
    }
  }, [subTabValue, setSubTabValueParam, defaultTab, isBlockedTab])

  const handleSelectTab = (newTab: string) => {
    setSubTabValueParam(newTab)
  }

  const form = useForm<UserFormData>({
    resolver: zodResolver(UserFormSchema),
    defaultValues: mapUserToFormData(defaultUserData),
  })

  useEffect(() => {
    form.reset(mapUserToFormData(defaultUserData))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [defaultUserData])

  const handleCancelClick = () => {
    const apiParams = getApiRequestParamsByUrl()
    router.push(`/users?${apiParams}`)
  }

  const handleCreateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)

    const mappedData: User = { ...parsed, groups: defaultUserData?.groups || [] }
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createUserData } = mappedData
    createUser.mutate(createUserData, {
      onSuccess: ({ data }) => {
        router.push(`/users/${data.id}`)
      },
    })
  }

  const handleUpdateUser = async (formData: UserFormData) => {
    const parsed = UserFormSchema.parse(formData)

    const updateUserData = { ...parsed, groups: defaultUserData?.groups || [] }
    updateUser.mutate(updateUserData, {
      onSuccess: ({ data }) => {
        setDefaultUserData(data)
        setIsReadOnly(true)
      },
    })
  }

  const handleSave = isEditMode ? form.handleSubmit(handleUpdateUser) : form.handleSubmit(handleCreateUser)

  let Content = <ContentCard>No data</ContentCard>
  if (userData) {
    switch (subTabValue) {
      case tabValues.userData.value:
      case '':
        Content = (
          <UserForm
            userData={userData}
            isEditMode={isEditMode}
            form={form}
            isReadOnly={isReadOnly}
            setIsReadOnly={setIsReadOnly}
            defaultUserData={defaultUserData}
            isLoading={isLoading}
            setIsSaveButtonDisabled={setIsSaveButtonDisabled}
          />
        )
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

  const customElement = (
    <ActionButtons
      confirmButtonType="button"
      onCancelClick={handleCancelClick}
      onConfirmClick={handleSave}
      isConfirmButtonDisabled={isSaveButtonDisabled}
      cancelButtonTitle={tCommon('actions.exit')}
      hasCard={false}
      className='px-6 py-0'
      wrapperClassname='w-auto'
    />
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        subTabs={{ tabs: tabs, selectedTab: subTabValue || defaultTab, onClick: newTab => handleSelectTab(newTab) }}
        customElement={!isReadOnly ? customElement : undefined}
      />
      {userData ? Content : <NoDataPage title={t('notFound')} />}
    </PageContainer>
  )
}
