'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
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
  const getTitle = () => {
    if (!isEditMode) {
      return t('newUser')
    } else if (isEditMode && userData) {
      return userData.displayName
    } else {
      return t('notFound')
    }
  }

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
    <PageContainer headerType="withSubTabs">
      <PageHeader
        title={getTitle()}
        subTabs={{ tabs: tabs, selectedTab, onClick: newTab => handleSelectTab(newTab) }}
      />
      <PageBackground>
        {userData ? (
          <ContentCard>
            {selectedTab === tabValues.roles.value && <RolesTab userId={userData.id} />}
            {selectedTab === tabValues.userData.value && <UserForm userData={userData} isEditMode={isEditMode} />}
          </ContentCard>
        ) : (
          <div>No data</div>
        )}
      </PageBackground>
    </PageContainer>
  )
}
