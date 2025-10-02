'use client'

import { PageHeader } from '@/components/page-header/PageHeader'
import React, { useState } from 'react'
import { Authority, FormUser, UserForm } from './UserForm'
import { AccessibleSelectProps } from '@/components/form/fields/Select'
import { useTranslations } from 'next-intl'
import { Tab } from '@/components/page-header/components/TabsSections'

interface UserDetailsProps {
  userData: FormUser | null
  userGroups: AccessibleSelectProps<FormUser>['options']
  isEditMode?: boolean
  authorities: Authority[]
}

export const UserDetails = (props: UserDetailsProps) => {
  const { userData, userGroups, authorities, isEditMode = false } = props
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
        (userData ? (
          <UserForm userData={userData} isEditMode userGroups={userGroups} authorities={authorities} />
        ) : (
          <div>User not found</div>
        ))}
    </div>
  )
}
