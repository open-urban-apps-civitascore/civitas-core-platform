'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/useQueryParams'

import { RolesTab } from './RolesTab'
import { FormUser, UserForm } from './UserForm'

interface UserDetailsProps {
  userData: FormUser | null
  isEditMode?: boolean
}

export const UserDetails = (props: UserDetailsProps) => {
  const { userData, isEditMode = false } = props
  const t = useTranslations('users')
  const { setSubTabValueParam, subTabValue } = useQueryParams()

  console.log(subTabValue)

  const tabValues = {
    userData: {
      label: t('detailsTabs.userData'),
      value: 'userDetails',
    },
    roles: {
      label: t('detailsTabs.roles'),
      value: 'roles',
    },
    userGroups: {
      label: t('detailsTabs.userGroups'),
      value: 'userGroups',
    },
    dataSpaces: {
      label: t('detailsTabs.dataspaces'),
      value: 'dataspaces',
    },
    account: {
      label: t('detailsTabs.account'),
      value: 'account',
    },
  }
  const tabs: Tab[] = [
    tabValues.userData,
    tabValues.roles,
    tabValues.userGroups,
    tabValues.dataSpaces,
    tabValues.account,
  ]
  const [selectedTab, setSelectedTab] = useState<string>((subTabValue as string) || tabValues.userData.value)

  useEffect(() => {
    setSelectedTab(subTabValue || tabValues.userData.value)
  }, [subTabValue, tabValues.userData.value])

  const handleSelectTab = (newTab: string) => {
    setSubTabValueParam(newTab)
  }

  return (
    <div>
      <PageHeader
        title={userData?.displayName}
        subTabs={{ tabs: tabs, selectedTab, onClick: newTab => handleSelectTab(newTab) }}
      />
      {selectedTab === tabValues.roles.value && userData && <RolesTab userId={userData.id} />}
      {selectedTab === tabValues.userData.value &&
        (userData ? <UserForm userData={userData} isEditMode={isEditMode} /> : <div>User not found</div>)}
    </div>
  )
}
