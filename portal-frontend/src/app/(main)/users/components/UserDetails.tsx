'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useMemo } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { useQueryParams } from '@/hooks/useQueryParams'
import { UserResponse } from '@/types/users'

import { RolesTab } from './roles-tab/RolesTab'
import { UserForm } from './UserForm'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'

interface UserDetailsProps {
  title: string
  userData: UserResponse | null
  isEditMode?: boolean
}

export const UserDetails = (props: UserDetailsProps) => {
  const { title, userData, isEditMode = false } = props
  const t = useTranslations('users')
  const { setSubTabValueParam, subTabValue } = useQueryParams()

  const tabValues: Record<'userData' | 'roles' | 'groups' | 'dataSpaces' | 'account', Tab> = {
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
    dataSpaces: {
      label: t('detailsTabs.dataspaces'),
      value: 'dataspaces',
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
  const tabs: Tab[] = [tabValues.userData, tabValues.groups, tabValues.dataSpaces, tabValues.roles, tabValues.account]

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

  let Content = <LoadingSpinner className="h-full" />
  if (!userData) {
    Content = <ContentCard>No data</ContentCard>
  } else if (!isBlockedTab) {
    switch (subTabValue) {
      case tabValues.userData.value:
        Content = <UserForm userData={userData} isEditMode={isEditMode} />
        break
      case tabValues.roles.value:
        Content = <RolesTab groupIds={userData.groups} />
        break
      default:
        Content = <ContentCard>No data</ContentCard>
        break
    }
  }

  return (
    <PageContainer headerType="withSubTabs" className="overflow-hidden">
      <PageHeader
        title={title}
        subTabs={{ tabs: tabs, selectedTab: subTabValue, onClick: newTab => handleSelectTab(newTab) }}
      />
      <PageBackground className="overflow-y-auto">{Content}</PageBackground>
    </PageContainer>
  )
}
