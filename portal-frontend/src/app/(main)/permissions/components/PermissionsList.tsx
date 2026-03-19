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

export const PermissionsList = (props: PermissionsListProps) => {
  const { header, items, isLoading } = props
  const t = useTranslations()

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
                <TableCell className="px-3 h-12">{item}</TableCell>
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
