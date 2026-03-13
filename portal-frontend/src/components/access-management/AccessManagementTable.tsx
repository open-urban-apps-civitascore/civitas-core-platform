import {
  CellContext,
  createColumnHelper,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  PaginationState,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { Trash, UserPlus, X } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { DataTable } from '@/components/table/DataTable'
import { SortableTableHeader } from '@/components/table/sortable-table-header/SortableTableHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

export type GroupRoleAssignmentTable = {
  groupId: string
  groupName: string
  groupDescription?: string
  assignedRoles: { roleId: string; roleName: string }[]
}

interface AccessManagementTableProps {
  assignments: GroupRoleAssignmentTable[]
  onDeleteClick?: (id: string) => void
  onAddRoleClick?: (groupId: string) => void
  onDeleteRole?: (groupId: string, roleId: string) => void
  isLoading?: boolean
  isReadOnly?: boolean
}

const AssignedRoleButton = ({
  assignedRoles,
  isReadOnly = true,
  onDeleteRole,
}: {
  assignedRoles: { roleId: string; roleName: string }[]
  isReadOnly?: boolean
  onDeleteRole?: (roleId: string) => void
}) => {
  return (
    <div className="flex flex-wrap gap-1">
      {assignedRoles.map(role => (
        <Badge key={role.roleId} variant="secondary" className="relative group p-2 h-9">
          <span>{role.roleName}</span>
          {!isReadOnly && (
            <Button
              type="button"
              variant="ghost"
              size="normal"
              className="p-0 ml-2 opacity-70 hover:opacity-100"
              onClick={() => onDeleteRole?.(role.roleId)}
            >
              <X className="h-3 w-3" />
            </Button>
          )}
        </Badge>
      ))}
    </div>
  )
}

export const AccessManagementTable = ({
  assignments,
  onDeleteClick,
  onAddRoleClick,
  onDeleteRole,
  isLoading,
  isReadOnly = true,
}: AccessManagementTableProps) => {
  const t = useTranslations('accessManagement')

  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 10,
  })

  const [sorting, setSorting] = useState<SortingState>([])

  const columnHelper = createColumnHelper<GroupRoleAssignmentTable>()

  const columns = useMemo(
    () => [
      columnHelper.accessor('groupName', {
        header: ({ column }) => <SortableTableHeader column={column} title={t('tableHeaders.group')} />,
        cell: info => info.getValue(),
        meta: {
          style: {
            width: '25%',
            fontWeight: 500,
          },
        },
      }),

      columnHelper.accessor('groupDescription', {
        header: t('tableHeaders.description'),
        cell: info => info.getValue() || '-',
        meta: {
          style: {
            whiteSpace: 'nowrap',
            width: '30%',
            textOverflow: 'ellipsis',
            overflow: 'hidden',
          },
        },
      }),

      columnHelper.accessor('assignedRoles', {
        header: t('tableHeaders.role'),
        cell: info => (
          <div className="flex flex-row gap-1">
            <AssignedRoleButton
              isReadOnly={isReadOnly}
              assignedRoles={info.getValue()}
              onDeleteRole={roleId => onDeleteRole?.(info.row.original.groupId, roleId)}
            />
            {!isReadOnly && !info.getValue().length && (
              <Button
                className="w-40 bg-secondary text-xs text-secondary-foreground hover:bg-secondary/90"
                size="sm"
                onClick={() => onAddRoleClick?.(info.row.original.groupId)}
              >
                <UserPlus />
                {t('addRole')}
              </Button>
            )}
          </div>
        ),
        meta: {
          style: {
            minWidth: '40%',
          },
        },
      }),

      columnHelper.display({
        id: 'actions',
        header: t('tableHeaders.action'),
        enableHiding: true,
        cell: ({ row }: CellContext<GroupRoleAssignmentTable, unknown>) => (
          <TableDropdownMenu
            classNameDropdownContent="w-45"
            menuItems={[
              {
                label: t('addRole'),
                onClick: () => onAddRoleClick?.(row.original.groupId),
                icon: UserPlus,
              },
              {
                label: t('deleteGroup'),
                onClick: () => onDeleteClick?.(row.original.groupId),
                icon: Trash,
              },
            ]}
          />
        ),
        meta: {
          style: {
            width: '5%',
          },
        },
      }),
    ],
    [columnHelper, t, isReadOnly, onDeleteClick, onAddRoleClick, onDeleteRole],
  )

  const table = useReactTable({
    getRowId: row => row.groupId,
    data: assignments,
    columns,
    state: {
      pagination,
      sorting,
      columnVisibility: {
        actions: !isReadOnly,
      },
    },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
  })

  return (
    <DataTable
      table={table}
      totalPages={table.getPageCount()}
      isLoading={isLoading}
      pageSize={pagination.pageSize}
      pageIndex={pagination.pageIndex}
      isPaginationHidden={assignments.length <= 10}
    />
  )
}
