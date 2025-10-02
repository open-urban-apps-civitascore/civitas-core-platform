'use client'

import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import { FormUser, UserForm } from './UserForm'

interface UserDetailsProps {
  userData: FormUser | null
  isEditMode?: boolean
}

export const UserDetails = (props: UserDetailsProps) => {
  const { userData, isEditMode = false } = props
  const t = useTranslations('users')
  const tabs: Tab[] = [
    {
      label: t('detailsTabs.userData'),
      value: 'userData',
    },
    {
      label: t('detailsTabs.roles'),
      value: 'roles',
    },
    {
      label: t('detailsTabs.userGroups'),
      value: 'userGroups',
    },
    {
      label: t('detailsTabs.dataspaces'),
      value: 'dataspaces',
    },
    {
      label: t('detailsTabs.account'),
      value: 'account',
    },
  ]
  const [selectedTab, setSelectedTab] = useState<string>(tabs[0].value)

  return (
    <div>
      <PageHeader
        title={userData?.displayName}
        subTabs={{ tabs: tabs, selectedTab, onClick: newTab => setSelectedTab(newTab) }}
      />
      {selectedTab === 'userData' &&
        (userData ? <UserForm userData={userData} isEditMode={isEditMode} /> : <div>User not found</div>)}
    </div>
  )
}
