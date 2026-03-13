'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useMemo, useState } from 'react'

import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Form } from '@/components/ui/form'
import { Datasource, DATASOURCE_STATUS_TYPES, DatasourceStatusType, DatasourceTab } from '@/types/datasources'
import { Group } from '@/types/groups'
import { Role } from '@/types/roles'
import { hasAssignmentChanges } from '@/utils/assignments'

import { useDatasourceForm } from '../hooks/useDatasourceForm'
import { AccessManagementTab } from './access-management/AccessManagementTab'
import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { ConnectorTab } from './connector-tab/ConnectorTab'

interface DatasourceOverviewProps {
  datasource: Datasource
  initialAssignments: GroupRoleAssignmentTable[]
  groups: Group[]
  roles: Role[]
}

const tabs: Tab<DatasourceTab>[] = [
  { value: 'basicInfo', label: 'datasources.tabs.basicInfo' },
  { value: 'connector', label: 'datasources.tabs.connector' },
  { value: 'dataStructure', label: 'datasources.tabs.dataStructure' },
  { value: 'accessManagement', label: 'datasources.tabs.accessManagement' },
]

const disabledTabs: DatasourceTab[] = ['dataStructure']

export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const { datasource, initialAssignments, groups, roles } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()
  const [isReadOnly, setIsReadOnly] = useState(searchParams.get('mode') !== 'edit')
  const [assignedGroups, setAssignedGroups] = useState<GroupRoleAssignmentTable[]>(initialAssignments)

  useEffect(() => {
    setIsReadOnly(searchParams.get('mode') !== 'edit')
  }, [searchParams])

  const updateMode = useCallback(
    (isEditing: boolean) => {
      setIsReadOnly(!isEditing)
      const params = new URLSearchParams(searchParams.toString())
      if (isEditing) {
        params.set('mode', 'edit')
      } else {
        params.delete('mode')
      }
      const query = params.toString()
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
    },
    [pathname, searchParams, router],
  )

  const {
    form,
    readyConnectorType,
    dataSourceStatus,
    handleStatusChange,
    canSetAvailable,
    completedTabs,
    submitDatasource,
    isLoading,
  } = useDatasourceForm(datasource, assignedGroups, initialAssignments)

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const areAssignmentsDirty = useMemo(
    () => hasAssignmentChanges(assignedGroups, initialAssignments),
    [assignedGroups, initialAssignments],
  )

  const handleSave = () => {
    submitDatasource(() => {
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
      router.refresh()
    })
  }

  const handleExit = () => {
    if (form.formState.isDirty || areAssignmentsDirty) {
      setIsExitModalOpen(true)
    } else {
      form.reset()
      updateMode(false)
      setAssignedGroups(initialAssignments)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    form.reset()
    setAssignedGroups(initialAssignments)
    updateMode(false)
  }

  const handleSaveAndExit = () => {
    submitDatasource(() => {
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
      setIsExitModalOpen(false)
      updateMode(false)
    })
  }

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={form} isReadOnly={isReadOnly} />
      case 'connector':
        return <ConnectorTab form={form} readyConnectorType={readyConnectorType} isReadOnly={isReadOnly} />
      case 'accessManagement':
        return (
          <AccessManagementTab
            assignedGroups={assignedGroups}
            onAssignedGroupsChange={setAssignedGroups}
            groups={groups}
            roles={roles}
            isReadOnly={isReadOnly}
          />
        )
      case 'dataStructure':
      default:
        return null
    }
  }

  return (
    <PageContainer testId="datasourceOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={datasource.name}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          disabledTabs,
          tabsWithNoCompletionStatus: ['accessManagement'], // Access Management tab has no required fields, so it should not show completion status
          hasCompletionStatus: true,
        }}
        customElement={
          <PageEditControls<DatasourceStatusType>
            status={dataSourceStatus}
            onStatusChange={handleStatusChange}
            statusOptions={Object.values(DATASOURCE_STATUS_TYPES)}
            canSetAvailable={canSetAvailable}
            confirmButtonType="button"
            onConfirmClick={handleSave}
            isConfirmButtonDisabled={
              (!form.formState.isDirty && !areAssignmentsDirty) ||
              !!form.formState.errors.name ||
              (dataSourceStatus !== DATASOURCE_STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
              isLoading
            }
            isCancelButtonDisabled={isLoading}
            onCancelClick={handleExit}
            hasCard={false}
            isReadOnly={isReadOnly}
            onEditClick={() => updateMode(true)}
            cancelButtonTitle={tCommon('actions.exit')}
            wrapperClassname="w-auto"
          />
        }
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <Form {...form}>
          <form
            data-testid="datasourceEditForm"
            aria-label={`${tCommon('form')} ${t('edit.basicInfo.title')}`}
            onSubmit={e => e.preventDefault()}
          >
            {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        isLoading={isLoading}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
      />
    </PageContainer>
  )
}
