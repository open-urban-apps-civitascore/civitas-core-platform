import { DialogProps } from '@radix-ui/react-dialog'
import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  PaginationState,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef, useState } from 'react'

import { useGetRoles } from '@/app/services/api/roles/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Role } from '@/types/roles'

import { SearchHeader } from '../search-area/SearchArea'

interface AddRoleModalProps extends DialogProps {
  assignedRoleIds: string[]
  onAddRoles: (roles: Role[]) => void
  groupName: string
}

export const AddRoleModal = (props: AddRoleModalProps) => {
  const { assignedRoleIds, open, onOpenChange = () => {}, onAddRoles, groupName } = props

  const t = useTranslations('accessManagement')
  const tCommon = useTranslations('common')

  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 10,
  })

  const [sorting, setSorting] = useState<SortingState>([])
  const [selection, setSelection] = useState<RowSelectionState>({})
  const [searchInput, setSearchInput] = useState('')

  // Track selected Role objects across pages since server-side pagination
  // only keeps the current page's data in the response
  const selectedRolesRef = useRef<Map<string, Role>>(new Map())

  const assignedRoleIdsSet = useMemo(() => new Set(assignedRoleIds), [assignedRoleIds])

  const queryParams = useMemo(() => {
    const params = new URLSearchParams({
      page: String(pagination.pageIndex),
      size: String(pagination.pageSize),
      roleType: 'DATA,GOVERNANCE',
    })
    if (sorting.length > 0) {
      params.set('sort', `${sorting[0].id},${sorting[0].desc ? 'desc' : 'asc'}`)
    }
    if (searchInput) {
      params.set('q', searchInput)
    }
    return params
  }, [pagination.pageIndex, pagination.pageSize, sorting, searchInput])

  const { data: rolesResponse, isLoading, isError } = useGetRoles({ params: queryParams })

  const roles = rolesResponse?.data ?? []
  const totalPages = rolesResponse?.totalPages ?? 0
  const hasData = roles.length > 0 || isLoading

  const resetState = () => {
    setSelection({})
    setPagination({ pageIndex: 0, pageSize: 10 })
    setSorting([])
    setSearchInput('')
    selectedRolesRef.current.clear()
  }

  useEffect(() => {
    if (!open) {
      resetState()
    }
  }, [open])

  // Reset page index when search changes
  useEffect(() => {
    setPagination(prev => ({ ...prev, pageIndex: 0 }))
  }, [searchInput])

  const handleSelectionChange = (updater: RowSelectionState | ((old: RowSelectionState) => RowSelectionState)) => {
    const newSelection = typeof updater === 'function' ? updater(selection) : updater
    // Track newly selected roles, ignore already-assigned ones
    const addedIds = Object.keys(newSelection).filter(id => !selection[id] && !assignedRoleIdsSet.has(id))
    const removedIds = Object.keys(selection).filter(id => !newSelection[id] && !assignedRoleIdsSet.has(id))
    for (const id of addedIds) {
      const role = roles.find(r => r.id === id)
      if (role) selectedRolesRef.current.set(id, role)
    }
    for (const id of removedIds) {
      selectedRolesRef.current.delete(id)
    }
    // Keep assigned IDs out of selection (they're shown as checked+disabled via the cell renderer)
    const filtered: RowSelectionState = {}
    for (const id of Object.keys(newSelection)) {
      if (!assignedRoleIdsSet.has(id)) {
        filtered[id] = true
      }
    }
    setSelection(filtered)
  }

  const columnHelper = createColumnHelper<Role>()

  const columns = useMemo(
    () => [
      columnHelper.accessor('id', {
        header: ({ table }) => (
          <Checkbox
            checked={
              table.getIsAllPageRowsSelected() ? true : table.getIsSomePageRowsSelected() ? 'indeterminate' : false
            }
            onCheckedChange={value => table.toggleAllPageRowsSelected(!!value)}
            aria-label={t('addRoleModal.selectAll')}
          />
        ),
        cell: ({ row }) => {
          const isAssigned = assignedRoleIdsSet.has(row.original.id)
          return (
            <Checkbox
              checked={isAssigned || row.getIsSelected()}
              disabled={isAssigned}
              onCheckedChange={value => row.toggleSelected(!!value)}
              aria-label={`${t('addRoleModal.selectRole')} ${row.original.name}`}
            />
          )
        },
        meta: {
          style: { width: '5%' },
        },
      }),

      columnHelper.accessor('name', {
        header: ({ column }) => <SortableTableHeader column={column} title={t('addRoleModal.tableHeaders.name')} />,
        cell: (info: CellContext<Role, unknown>) => <div className="font-medium">{info.getValue() as string}</div>,
        meta: {
          style: { width: '25%' },
        },
      }),

      columnHelper.accessor('description', {
        header: t('addRoleModal.tableHeaders.description'),
        cell: info => info.getValue(),
        meta: {
          style: { width: '45%' },
        },
      }),

      columnHelper.accessor('readonly', {
        header: t('addRoleModal.tableHeaders.roleType'),
        cell: info =>
          info.getValue() === true ? t('addRoleModal.roleTypes.default') : t('addRoleModal.roleTypes.custom'),
        meta: {
          style: { width: '25%' },
        },
      }),
    ],
    [columnHelper, t, assignedRoleIdsSet],
  )

  const table = useReactTable({
    getRowId: row => row.id,
    data: roles,
    columns,
    pageCount: totalPages,
    state: {
      pagination,
      sorting,
      rowSelection: selection,
    },
    manualPagination: true,
    manualSorting: true,
    getCoreRowModel: getCoreRowModel(),
    enableRowSelection: row => !assignedRoleIdsSet.has(row.original.id),
    onRowSelectionChange: handleSelectionChange,
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
  })

  const newlySelectedCount = selectedRolesRef.current.size

  const onConfirmClick = () => {
    if (newlySelectedCount > 0) {
      onAddRoles(Array.from(selectedRolesRef.current.values()))
    }
    onOpenChange(false)
  }

  const onCancelClick = () => {
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="block sm:max-w-[95%] sm:w-[95%] md:max-w-225 h-[80%] max-h-175 [--title-height:64px] [--button-height:60px] [--search-height:64px]">
        <DialogHeader className="mb-4">
          <DialogTitle>{t('addRoleModal.title')}</DialogTitle>
          <DialogDescription>{t('addRoleModal.description', { groupName })}</DialogDescription>
        </DialogHeader>

        <div className="relative h-[calc(100%-var(--title-height)-var(--button-height))]">
          {!hasData && !isError ? (
            <div className="flex items-center justify-center h-full text-center text-muted-foreground">
              {t('addRoleModal.noRolesAvailable')}
            </div>
          ) : isError ? (
            <div className="flex items-center justify-center h-full text-center text-muted-foreground">
              {tCommon('errors.loadingError')}
            </div>
          ) : (
            <>
              <div className="h-[var(--search-height)] mb-4">
                <SearchHeader searchString={searchInput} onChangeSearchString={value => setSearchInput(value)} />
              </div>
              <div className="h-[calc(100%-var(--search-height)-4rem)]">
                <DataTable
                  table={table}
                  pageIndex={pagination.pageIndex}
                  pageSize={pagination.pageSize}
                  totalPages={totalPages}
                  isLoading={isLoading}
                />
              </div>

              <div className="absolute bottom-6 left-4 text-sm text-muted-foreground">
                {t('addRoleModal.selectedRoles', {
                  number: newlySelectedCount,
                })}
              </div>
            </>
          )}
        </div>

        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={onConfirmClick}
          onCancelClick={onCancelClick}
          isConfirmButtonDisabled={newlySelectedCount === 0}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
          cancelButtonTitle={tCommon('actions.cancel')}
        />
      </DialogContent>
    </Dialog>
  )
}
