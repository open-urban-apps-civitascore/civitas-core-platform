'use client'

import { DialogProps } from '@radix-ui/react-dialog'
import {
  createColumnHelper,
  getCoreRowModel,
  PaginationState,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { Info } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useQueryParams } from '@/hooks/use-query-params'
import { Role, ROLE_TYPES } from '@/types/roles'
import { isPageIndexHigherThanTotalPages, resolveUpdater } from '@/utils/table'

interface AssignRoleModalProps extends DialogProps {
  groupName: string
  roleType: typeof ROLE_TYPES.SYSTEM | typeof ROLE_TYPES.DATA
  assignedRoleIds: string[]
  onAssignRoles: (roles: Role[]) => void
}

export const AssignRoleModal = (props: AssignRoleModalProps) => {
  const { groupName, roleType, assignedRoleIds, open, onOpenChange = () => {}, onAssignRoles } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const [selection, setSelection] = useState<RowSelectionState>({})
  const { getApiRequestParams } = useQueryParams()

  const roleTypeFilter = roleType === ROLE_TYPES.SYSTEM ? ROLE_TYPES.SYSTEM : ROLE_TYPES.DATA
  const rolesParams = getApiRequestParams({ pageIndex, pageSize, sorting, search: searchString })
  rolesParams.set('roleType', roleTypeFilter)

  const { data: rolesData, isFetching: isFetchingRoles } = useGetRoles({ params: rolesParams })

  const roles = rolesData?.data ?? []
  const rowCount = rolesData?.totalElements || 0
  const totalPages = Math.ceil(rowCount / pageSize)

  useEffect(() => {
    if (!open) {
      setSelection({})
      setSearchString('')
      setPageIndex(0)
    }
  }, [open])

  useEffect(() => {
    if (isPageIndexHigherThanTotalPages(pageIndex, totalPages)) {
      setPageIndex(totalPages - 1)
    }
  }, [pageIndex, totalPages])

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const setFocus = (elementId: string) => {
    requestAnimationFrame(() => {
      const element = document.getElementById(elementId)
      element?.focus()
    })
  }

  const handleAssign = () => {
    const selectedRoleIds = Object.keys(selection).filter(key => selection[key])
    const selectedRoles = roles.filter(r => selectedRoleIds.includes(r.id))
    onAssignRoles(selectedRoles)
  }

  const isDataRole = roleType === ROLE_TYPES.DATA
  const modalTitle = isDataRole ? t('roles.assignDataRole') : t('roles.assignSystemRole')
  const modalDescription = isDataRole
    ? t('roles.assignDataRoleDescription', { groupName })
    : t('roles.assignSystemRoleDescription', { groupName })

  const columnHelper = createColumnHelper<Role>()

  const columns = [
    columnHelper.accessor('id', {
      header: () => (
        <Checkbox
          checked={
            table.getIsAllPageRowsSelected() ? true : table.getIsSomePageRowsSelected() ? 'indeterminate' : false
          }
          onCheckedChange={value => table.toggleAllPageRowsSelected(!!value)}
          aria-label="Select all roles"
          id="selectAll"
        />
      ),
      cell: ({ row }) => {
        const isAssigned = assignedRoleIds.includes(row.original.id)
        return (
          <Checkbox
            checked={isAssigned || row.getIsSelected()}
            onCheckedChange={value => {
              row.toggleSelected(!!value)
              setFocus(row.id)
            }}
            disabled={isAssigned}
            aria-label={`Select role ${row.original.name}`}
            id={row.id}
          />
        )
      },
      enableSorting: false,
      enableHiding: false,
    }),
    columnHelper.accessor('name', {
      header: ({ column }) => <SortableTableHeader column={column} title={t('roles.columns.name')} />,
      cell: info => info.getValue(),
      meta: {
        style: {
          width: '22.22%',
          minWidth: '200px',
        },
      },
    }),
    columnHelper.accessor('description', {
      header: t('roles.columns.description'),
      cell: info => info.getValue() ?? '',
      enableSorting: false,
    }),
    columnHelper.accessor('readonly', {
      header: t('roles.columns.type'),
      cell: info => {
        return info.getValue() ? t('roles.originLabels.default') : t('roles.originLabels.custom')
      },
      enableSorting: false,
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns,
    data: roles,
    rowCount,
    state: {
      pagination: { pageIndex, pageSize },
      sorting,
      rowSelection: selection,
    },
    enableRowSelection: row => !assignedRoleIds.includes(row.original.id),
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    onPaginationChange: updater => {
      handlePagination(resolveUpdater(updater, { pageIndex, pageSize }))
    },
    onRowSelectionChange: setSelection,
    onSortingChange: updater => setSorting(resolveUpdater(updater, sorting)),
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex flex-col sm:max-w-[95%] sm:w-[95%] md:max-w-[1061px] h-[80%] max-h-[743px]">
        <DialogHeader>
          <DialogTitle>{modalTitle}</DialogTitle>
          <DialogDescription>{modalDescription}</DialogDescription>
        </DialogHeader>
        {isDataRole && (
          <div className="flex items-center gap-2 p-3 rounded-md bg-muted text-muted-foreground text-sm">
            <Info className="h-4 w-4 shrink-0" />
            <span>{t('roles.dataRolePlatformWarning')}</span>
          </div>
        )}
        <SearchHeader
          searchString={searchString}
          onChangeSearchString={newSearchString => setSearchString(newSearchString)}
        />
        <div className="min-h-0 flex-1 overflow-auto">
          <DataTable
            table={table}
            pageIndex={pageIndex}
            pageSize={pageSize}
            totalPages={totalPages}
            isLoading={isFetchingRoles}
          />
        </div>
        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={handleAssign}
          onCancelClick={() => onOpenChange(false)}
          isConfirmButtonDisabled={isFetchingRoles || !Object.values(selection).some(Boolean)}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
        />
      </DialogContent>
    </Dialog>
  )
}
