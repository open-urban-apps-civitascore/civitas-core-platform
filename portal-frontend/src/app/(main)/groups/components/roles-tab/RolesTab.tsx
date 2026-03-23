'use client'

import { createColumnHelper, getCoreRowModel, getSortedRowModel, Row, useReactTable } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { LinkCell } from '@/components/table/link-cell/LinkCell'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { TableContainer } from '@/components/table-container/TableContainer'
import { AlertBox } from '@/components/text-box/TextBox'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { usePermissions } from '@/hooks/use-permissions'
import { Assignment } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { AssignmentFormData, Group, GroupBaseFormData } from '@/types/groups'
import { Role, ROLE_TYPES } from '@/types/roles'

import { AssignRoleModal } from './AssignRoleModal'

const columnHelper = createColumnHelper<Assignment>()

type ScopeTab = 'platform' | 'datasets' | 'datasources' | 'datastructures'

const SCOPE_TAB_CONFIG: Record<ScopeTab, { scopeType: string | null }> = {
  platform: { scopeType: null },
  datasets: { scopeType: 'DATASET' },
  datasources: { scopeType: 'DATASOURCE' },
  datastructures: { scopeType: 'DATASTRUCTURE' },
}

interface RolesTabProps {
  form: UseFormReturn<GroupBaseFormData>
  groupData: Group
  isReadOnly: boolean
  pendingRoles: Role[]
  setPendingRoles: React.Dispatch<React.SetStateAction<Role[]>>
}

export const RolesTab = (props: RolesTabProps) => {
  const { form, groupData, isReadOnly, pendingRoles, setPendingRoles } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const { hasPermission } = usePermissions()

  const [activeScopeTab, setActiveScopeTab] = useState<ScopeTab>('platform')
  const [searchString, setSearchString] = useState('')
  const [assignmentToRemove, setAssignmentToRemove] = useState<string | null>(null)
  const [isRemoveWarningOpen, setIsRemoveWarningOpen] = useState(false)
  const [assignModalRoleType, setAssignModalRoleType] = useState<
    typeof ROLE_TYPES.SYSTEM | typeof ROLE_TYPES.DATA | null
  >(null)

  const isPlatformTab = activeScopeTab === 'platform'

  const formAssignments = form.watch('assignments')

  const existingAssignments: Assignment[] = useMemo(() => {
    return groupData.assignments ?? []
  }, [groupData.assignments])

  const displayAssignments: Assignment[] = useMemo(() => {
    // Keep existing assignments that are still in formAssignments
    const kept = existingAssignments.filter(ea =>
      formAssignments.some(fa => fa.roleId === ea.role.id && (fa.scopeType ?? null) === (ea.scopeType ?? null)),
    )

    // Add newly added assignments (in formAssignments but not in existingAssignments)
    const added: Assignment[] = formAssignments
      .filter(
        fa =>
          !existingAssignments.some(
            ea => ea.role.id === fa.roleId && (fa.scopeType ?? null) === (ea.scopeType ?? null),
          ),
      )
      .map(fa => {
        const role = pendingRoles.find(r => r.id === fa.roleId)
        return {
          id: `pending-${fa.roleId}`,
          createdAt: '',
          modifiedAt: '',
          group: { id: fa.groupId, name: groupData.name },
          role: {
            id: fa.roleId,
            name: role?.name ?? '',
            roleType: role?.roleType ?? 'SYSTEM',
            description: role?.description ?? '',
            readonly: role?.readonly ?? false,
          },
          scopeType: fa.scopeType ?? null,
          scope: fa.scopeId ? { id: fa.scopeId, name: '' } : null,
        }
      })

    return [...kept, ...added]
  }, [existingAssignments, formAssignments, pendingRoles, groupData.name])

  const filteredAssignments = useMemo(() => {
    let filtered = displayAssignments.filter(a => {
      if (isPlatformTab) {
        return a.scopeType === null || a.scopeType === 'TENANT'
      }
      return a.scopeType === SCOPE_TAB_CONFIG[activeScopeTab].scopeType
    })

    if (searchString.trim()) {
      const search = searchString.toLowerCase()
      filtered = filtered.filter(a => a.role.name.toLowerCase().includes(search))
    }

    return filtered
  }, [displayAssignments, activeScopeTab, isPlatformTab, searchString])

  const assignedRoleIds = useMemo(() => {
    return formAssignments
      .filter(a => a.scopeType === null || a.scopeType === 'TENANT' || a.scopeType === undefined)
      .map(a => a.roleId)
  }, [formAssignments])

  const handleRemoveClick = (assignmentId: string) => {
    setAssignmentToRemove(assignmentId)
    setIsRemoveWarningOpen(true)
  }

  const handleConfirmRemove = () => {
    if (!assignmentToRemove) return
    const current = form.getValues('assignments')
    const toRemove = displayAssignments.find(a => a.id === assignmentToRemove)
    if (!toRemove) return
    form.setValue(
      'assignments',
      current.filter(a => !(a.roleId === toRemove.role.id && (a.scopeType ?? null) === (toRemove.scopeType ?? null))),
      { shouldDirty: true },
    )
    setPendingRoles(prev => prev.filter(r => r.id !== toRemove.role.id))
    setIsRemoveWarningOpen(false)
    setAssignmentToRemove(null)
  }

  const handleAssignRoles = (selectedRoles: Role[]) => {
    const current = form.getValues('assignments')
    const isDataRole = assignModalRoleType === ROLE_TYPES.DATA
    const newAssignments: AssignmentFormData[] = selectedRoles.map(role => ({
      groupId: groupData.id,
      roleId: role.id,
      scopeType: isDataRole ? ('TENANT' as const) : null,
      scopeId: null,
    }))
    form.setValue('assignments', [...current, ...newAssignments], { shouldDirty: true })
    setPendingRoles(prev => [...prev, ...selectedRoles])
    setAssignModalRoleType(null)
  }

  const columns = useMemo(
    () => [
      columnHelper.accessor('role.name', {
        id: 'name',
        header: ({ column }) => <SortableTableHeader column={column} title={t('roles.columns.name')} />,
        cell: ({ row }) => (
          <LinkCell href={`/roles/${row.original.role.id}`} isDisabled={!hasPermission(PERMISSION_NAMES.ROLE_READ)}>
            {row.original.role.name}
          </LinkCell>
        ),
        meta: {
          style: {
            width: '20%',
            minWidth: '150px',
          },
        },
      }),
      columnHelper.accessor('role.description', {
        id: 'description',
        header: t('roles.columns.description'),
        cell: info => info.getValue() ?? '',
        enableSorting: false,
      }),
      columnHelper.display({
        id: 'object',
        header: t('roles.columns.object'),
        cell: ({ row }) => {
          const roleType = row.original.role.roleType
          if (roleType === ROLE_TYPES.SYSTEM) return t('roles.objectLabels.SYSTEM')
          if (roleType === ROLE_TYPES.DATA) return t('roles.objectLabels.DATA')
          return roleType ?? ''
        },
        enableSorting: false,
      }),
      columnHelper.display({
        id: 'type',
        header: t('roles.columns.type'),
        cell: ({ row }) => {
          return row.original.role.readonly ? t('roles.originLabels.default') : t('roles.originLabels.custom')
        },
        enableSorting: false,
      }),
      columnHelper.display({
        id: 'scope',
        header: t('roles.columns.scope'),
        cell: ({ row }) => {
          if (isPlatformTab) return t('roles.scopePlatform')
          return row.original.scope?.name ?? ''
        },
        enableSorting: false,
      }),
      ...(!isReadOnly && isPlatformTab
        ? [
            {
              id: 'actions',
              cell: ({ row }: { row: Row<Assignment> }) => (
                <TableDropdownMenu
                  menuItems={[
                    {
                      label: tCommon('actions.removeItem', { item: tCommon('items.role') }),
                      onClick: () => handleRemoveClick(row.original.id),
                    },
                  ]}
                />
              ),
              meta: {
                style: {
                  width: '50px',
                },
              },
            },
          ]
        : []),
    ],
    [isReadOnly, isPlatformTab, t, tCommon, hasPermission],
  )

  const table = useReactTable({
    getRowId: row => row.id,
    columns,
    data: filteredAssignments,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    manualPagination: false,
    manualSorting: false,
  })

  const scopeTabs: { key: ScopeTab; label: string }[] = [
    { key: 'platform', label: t('roles.scopeTabs.platform') },
    { key: 'datasets', label: t('roles.scopeTabs.datasets') },
    { key: 'datasources', label: t('roles.scopeTabs.datasources') },
    { key: 'datastructures', label: t('roles.scopeTabs.datastructures') },
  ]

  const getScopedInfoBannerText = (): string | null => {
    if (activeScopeTab === 'datasets') return t('roles.scopedInfoBanner.DATASET')
    if (activeScopeTab === 'datasources') return t('roles.scopedInfoBanner.DATASOURCE')
    if (activeScopeTab === 'datastructures') return t('roles.scopedInfoBanner.DATASTRUCTURE')
    return null
  }

  const infoBannerText = getScopedInfoBannerText()

  const AddRoleDropdown = (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button">
          <Plus />
          {t('roles.addRole')}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={() => setAssignModalRoleType(ROLE_TYPES.SYSTEM)} className="hover:cursor-pointer">
          {t('roles.systemRole')}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => setAssignModalRoleType(ROLE_TYPES.DATA)} className="hover:cursor-pointer">
          {t('roles.dataRole')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )

  const isEmpty = filteredAssignments.length === 0 && !searchString.trim()

  return (
    <div className="h-full">
      <SearchHeader
        searchString={searchString}
        onChangeSearchString={setSearchString}
        customElement={
          !isReadOnly && isPlatformTab && hasPermission(PERMISSION_NAMES.ROLE_READ) ? AddRoleDropdown : undefined
        }
      />

      <div className="flex items-center justify-between gap-4 mb-4">
        <div className="flex gap-2" role="tablist">
          {scopeTabs.map(tab => (
            <Button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={activeScopeTab === tab.key}
              variant={activeScopeTab === tab.key ? 'default' : 'outline'}
              size="sm"
              onClick={() => {
                setActiveScopeTab(tab.key)
                setSearchString('')
              }}
            >
              {tab.label}
            </Button>
          ))}
        </div>

        {infoBannerText && <AlertBox text={infoBannerText} />}
      </div>

      {isEmpty ? (
        <NoDataPage title={t('roles.noRoles')} subTitle={t('roles.noRolesDescription')} />
      ) : (
        <TableContainer className="pb-4">
          <DataTable
            table={table}
            pageIndex={0}
            pageSize={filteredAssignments.length || 10}
            totalPages={1}
            isLoading={false}
            isPaginationHidden
          />
        </TableContainer>
      )}

      <WarningModal
        open={isRemoveWarningOpen}
        title={t('roles.removeRole')}
        description={t('roles.removeRoleDescription')}
        onConfirm={handleConfirmRemove}
        onDiscard={() => {
          setIsRemoveWarningOpen(false)
          setAssignmentToRemove(null)
        }}
        confirmButtonTitle={tCommon('actions.remove')}
        isLoading={false}
      />

      {assignModalRoleType && (
        <AssignRoleModal
          open={!!assignModalRoleType}
          onOpenChange={open => {
            if (!open) setAssignModalRoleType(null)
          }}
          groupName={groupData.name}
          roleType={assignModalRoleType}
          assignedRoleIds={assignedRoleIds}
          onAssignRoles={handleAssignRoles}
        />
      )}
    </div>
  )
}
