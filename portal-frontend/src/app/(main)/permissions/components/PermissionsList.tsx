'use client'

import { useTranslations } from 'next-intl'

import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

interface PermissionsListProps {
  header: string
  items: string[]
  isLoading?: boolean
}

const LoadingSkeleton = () => (
  <div className="rounded-md border bg-white p-3">
    <Skeleton className="h-8 w-full mb-2" />
    <Skeleton className="h-8 w-full mb-2" />
    <Skeleton className="h-8 w-full" />
  </div>
)

export const PermissionsList = ({ header, items, isLoading }: PermissionsListProps) => {
  const t = useTranslations()

  const translateItem = (item: string): string => {
    const lastUnderscoreIndex = item.lastIndexOf('_')
    if (lastUnderscoreIndex === -1) return item

    const permission = item.substring(0, lastUnderscoreIndex)
    const action = item.substring(lastUnderscoreIndex + 1).toLowerCase()

    const permissionKey = `permissions.values.${permission}` as const
    const actionKey = `permissions.actions.${action}` as const

    if (!t.has(permissionKey) || !t.has(actionKey)) {
      return item
    }

    return t('permissions.systemPermissions.entry', {
      permission: t(permissionKey),
      action: t(actionKey),
    })
  }

  if (isLoading) {
    return <LoadingSkeleton />
  }

  return (
    <div className="rounded-md border bg-white">
      <Table>
        <TableHeader>
          <TableRow className="bg-secondary">
            <TableHead className="text-lg font-semibold px-3 h-14">{header}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.length > 0 ? (
            items.map(item => (
              <TableRow key={item}>
                <TableCell className="px-3 h-12">{translateItem(item)}</TableCell>
              </TableRow>
            ))
          ) : (
            <TableRow>
              <TableCell className="h-24 text-center">{t('common.noResults')}</TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </div>
  )
}
