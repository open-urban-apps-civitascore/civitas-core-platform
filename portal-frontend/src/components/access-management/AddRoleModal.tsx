import { DialogProps } from '@radix-ui/react-dialog'
import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  PaginationState,
  RowSelectionState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Role } from '@/types/roles'

import { SearchHeader } from '../search-area/SearchArea'

interface AddRoleModalProps extends DialogProps {
  roles: Role[]
  assignedRoleIds: string[]
  onAddRoles: (roleIds: string[]) => void
  groupName: string
}

export const AddRoleModal = (props: AddRoleModalProps) => {
  const { roles, assignedRoleIds, open, onOpenChange = () => {}, onAddRoles, groupName } = props

  const t = useTranslations('accessManagement')
  const tCommon = useTranslations('common')

  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 10,
  })

  const [sorting, setSorting] = useState<SortingState>([])
  const [selection, setSelection] = useState<RowSelectionState>({})
  const [searchInput, setSearchInput] = useState('')

  const availableRoles = useMemo(() => {
    return roles.filter(role => !assignedRoleIds.includes(role.id))
  }, [roles, assignedRoleIds])

  const resetSelection = () => {
    setSelection({})
    setPagination({ pageIndex: 0, pageSize: 10 })
    setSorting([])
    setSearchInput('')
  }

  useEffect(() => {
    if (!open) {
      resetSelection()
    }
  }, [open])

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
        cell: ({ row }) => (
          <Checkbox
            checked={row.getIsSelected()}
            onCheckedChange={value => row.toggleSelected(!!value)}
            aria-label={`${t('addRoleModal.selectRole')} ${row.original.name}`}
          />
        ),
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
    [columnHelper, t],
  )

  const table = useReactTable({
    getRowId: row => row.id,
    data: availableRoles,
    columns,
    state: {
      pagination,
      sorting,
      rowSelection: selection,
      globalFilter: searchInput,
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
    onRowSelectionChange: setSelection,
    getFilteredRowModel: getFilteredRowModel(),
    onGlobalFilterChange: setSearchInput,
  })

  const selectedRoleIds = Object.keys(selection)

  const onConfirmClick = () => {
    if (selectedRoleIds.length > 0) {
      onAddRoles(selectedRoleIds)
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
          {availableRoles.length === 0 ? (
            <div className="flex items-center justify-center h-full text-center text-muted-foreground">
              {t('addRoleModal.noRolesAvailable')}
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
                  totalPages={table.getPageCount()}
                  isLoading={false}
                />
              </div>

              <div className="absolute bottom-6 left-4 text-sm text-muted-foreground">
                {t('addRoleModal.selectedRoles', {
                  number: selectedRoleIds.length,
                })}
              </div>
            </>
          )}
        </div>

        <ActionButtons
          confirmButtonType="button"
          onConfirmClick={onConfirmClick}
          onCancelClick={onCancelClick}
          isConfirmButtonDisabled={selectedRoleIds.length === 0}
          hasCard={false}
          confirmButtonTitle={tCommon('actions.add')}
          cancelButtonTitle={tCommon('actions.cancel')}
        />
      </DialogContent>
    </Dialog>
  )
}
