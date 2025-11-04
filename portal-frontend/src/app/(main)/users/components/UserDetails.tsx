'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useMemo } from 'react'

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

  const tabValues: Record<'userData' | 'roles' | 'userGroups' | 'dataSpaces' | 'account', Tab> = {
    userData: {
      label: t('detailsTabs.userData'),
      value: 'userDetails',
      isActive: true,
    },
    roles: {
      label: t('detailsTabs.roles'),
      value: 'roles',
      isActive: isEditMode,
    },
    userGroups: {
      label: t('detailsTabs.userGroups'),
      value: 'userGroups',
      isActive: isEditMode,
    },
    dataSpaces: {
      label: t('detailsTabs.dataspaces'),
      value: 'dataspaces',
      isActive: isEditMode,
    },
    account: {
      label: t('detailsTabs.account'),
      value: 'account',
      isActive: isEditMode,
    },
  }
  const tabs: Tab[] = [
    tabValues.userData,
    tabValues.roles,
    tabValues.userGroups,
    tabValues.dataSpaces,
    tabValues.account,
  ]

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

  return (
    <PageContainer headerType="withSubTabs">
      <PageHeader
        title={getTitle()}
        subTabs={{ tabs: tabs, selectedTab: subTabValue, onClick: newTab => handleSelectTab(newTab) }}
      />
      <PageBackground>
        {userData ? (
          <ContentCard>
            {subTabValue === tabValues.roles.value && isEditMode && <RolesTab userId={userData.id} />}
            {subTabValue === tabValues.userData.value && <UserForm userData={userData} isEditMode={isEditMode} />}
          </ContentCard>
        ) : (
          <div>No data</div>
        )}
      </PageBackground>
    </PageContainer>
  )
}
