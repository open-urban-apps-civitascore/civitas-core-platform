'use client'

import { ScrollArea } from '@radix-ui/react-scroll-area'
import { createColumnHelper, flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { ChevronRight } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'

import type { DetailsFieldContainerProps } from '@/components/form/DetailsFieldContainer'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { getAriaSort } from '@/components/table/DataTable'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ScrollBar } from '@/components/ui/scroll-area'
import { Skeleton } from '@/components/ui/skeleton'
import { Table as ShadCnTable, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'
import { ROLE_TYPES, RoleType, UserRolesTableData } from '@/types/roles'

const LoadingSkeleton = () => (
  <>
    <Skeleton className="h-10 w-full mb-2.5 mt-2" />
    <Skeleton className="h-10 w-full mb-2.5" />
  </>
)

interface RolesCategoryProps extends DetailsFieldContainerProps {
  title: string
  rolesType: RoleType
  roles: UserRolesTableData[]
  isLoading: boolean
}

export const RoleCategory = (props: RolesCategoryProps) => {
  const { rolesType, title, roles, isLoading, className } = props
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const columnHelper = createColumnHelper<UserRolesTableData>()

  const columns = [
    columnHelper.accessor('id', {
      header: 'id',
      cell: info => info.getValue(),
      enableHiding: true,
    }),
    columnHelper.accessor('name', {
      header: t('roles.rolesTables.role'),
      cell: info => (
        <div className="flex justify-between items-center">
          <Badge
            variant="secondary"
            className="h-6 group-hover:border-input group-hover:bg-transparent group-hover:text-foreground"
          >
            {info.getValue()}
          </Badge>
          <Button
            onClick={() => router.push(`/roles/${info.row.id}`)}
            className="hidden group-hover:block group/button"
            variant="outline"
          >
            <ChevronRight className="group-hover/button:stroke-[3]" />
          </Button>
        </div>
      ),
      meta: {
        style: {
          width: '28%',
        },
      },
    }),
    columnHelper.accessor('inherited', {
      header: t('roles.rolesTables.inherited'),
      cell: info => (info.getValue() ? 'ja' : 'nein'),
      meta: {
        style: {
          width: '44%',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('dataspace', {
      header: t('roles.rolesTables.assignment'),
      cell: info =>
        info.getValue() ? (
          <div className="flex justify-between items-center">
            {info.getValue()?.title}
            <Button className="hidden group-hover:block group/button" variant="outline">
              <ChevronRight className="group-hover/button:stroke-[3]" />
            </Button>
          </div>
        ) : (
          '-'
        ),
      meta: {
        style: {
          width: '44%',
          fontWeight: '500',
        },
      },
    }),
    columnHelper.accessor('group', {
      header: t('roles.rolesTables.group'),
      cell: info => info.getValue() || '-',
      meta: {
        style: {
          width: '28%',
        },
      },
    }),
  ]

  const table = useReactTable({
    getRowId: row => row.id,
    columns: columns,
    data: roles,
    initialState: {
      columnVisibility: {
        id: false,
        inherited: rolesType === ROLE_TYPES.SYSTEM,
        dataspace: rolesType !== ROLE_TYPES.SYSTEM,
      },
    },
    getCoreRowModel: getCoreRowModel(),
  })
  return (
    <DetailsFieldContainer className={cn('min-h-21 grid grid-cols-[minmax(150px,15%)_auto]', className)}>
      <h3 className="flex items-center h-[40px] w-[150px] text-sm">{title}</h3>
      <div className="flex flex-wrap gap-2 items-center">
        <div className="@container h-full w-full">
          <div className="h-full ">
            <ScrollArea className="w-full bg-white rounded-md">
              <ShadCnTable aria-labelledby="subheading" tableContainerProps={{ className: '' }}>
                <TableHeader>
                  {table.getHeaderGroups().map(group => (
                    <TableRow key={group.id}>
                      {group.headers.map(header => (
                        <TableHead
                          key={header.id}
                          scope="col"
                          className="text-primary-light px-3"
                          aria-sort={getAriaSort(header.column.getIsSorted())}
                          style={header.column.columnDef.meta?.style}
                        >
                          {flexRender(header.column.columnDef.header, header.getContext())}
                        </TableHead>
                      ))}
                    </TableRow>
                  ))}
                </TableHeader>
                <TableBody>
                  {table.getRowModel().rows?.length ? (
                    table.getRowModel().rows.map(row => (
                      <TableRow
                        className={cn('group h-14')}
                        key={row.id}
                        style={{ borderWidth: 0, borderTopWidth: row.depth === 0 ? 1 : 0 }}
                      >
                        {row.getVisibleCells().map(cell => (
                          <TableCell
                            className="whitespace-normal px-3"
                            key={cell.id}
                            style={cell.column.columnDef.meta?.style}
                          >
                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                          </TableCell>
                        ))}
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={table.getAllColumns().length} className="h-24 text-center">
                        {isLoading ? <LoadingSkeleton /> : tCommon('noResults')}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </ShadCnTable>
              <ScrollBar orientation="horizontal" />
            </ScrollArea>
          </div>
        </div>
      </div>
    </DetailsFieldContainer>
  )
}
