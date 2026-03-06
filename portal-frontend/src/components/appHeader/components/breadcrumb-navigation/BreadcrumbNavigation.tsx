'use client'

import { ArrowLeft, Slash } from 'lucide-react'
import { useParams, usePathname, useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import React from 'react'

import { useGetBredcrumbs } from '@/app/services/api/breadcrumbs/clientRequests'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'

export type Breadcrumb = {
  title: string
  href: string
  isLast: boolean
  isDynamic: boolean
}

export type BreadcrumbApiResponse = {
  name?: string
  firstName?: string
  lastName?: string
  title?: string
}

const getName = (firstName?: string, lastName?: string) => (firstName && lastName ? `${firstName} ${lastName}` : null)

export const BreadcrumbNavigation = () => {
  const pathname = usePathname()
  const params = useParams()
  const router = useRouter()
  const t = useTranslations('sidebar')
  const tCommon = useTranslations('common')

  const segments = pathname?.split('/').filter(Boolean) ?? []

  const breadcrumbs: Breadcrumb[] = segments.map((segment, index) => ({
    title: segment,
    href: `/${segments.slice(0, index + 1).join('/')}`,
    isLast: index === segments.length - 1,
    isDynamic: Object.values(params).includes(segment),
  }))

  const results = useGetBredcrumbs(breadcrumbs)

  const updatedBreadcrumbs = breadcrumbs.map((crumb, index) => {
    const data = results[index]?.data?.data
    const title = crumb.isDynamic
      ? data?.name || getName(data?.firstName, data?.lastName) || data?.title || crumb.title
      : t(crumb.title)
    return { ...crumb, title }
  })

  const CustomBreadcrumbSeparator = () => (
    <BreadcrumbSeparator aria-hidden className="hidden md:block">
      <Slash />
    </BreadcrumbSeparator>
  )

  const hasBackButton = breadcrumbs.some(crumb => crumb.isLast && crumb.isDynamic)
  const parentPath = pathname ? pathname.split('/').slice(0, -1).join('/') || '/' : undefined

  if (!pathname) return null

  return (
    <div className="flex items-center gap-4">
      {hasBackButton && parentPath && (
        <Button
          variant="outline"
          size="icon"
          className="h-7 w-7"
          aria-label={tCommon('actions.back')}
          onClick={() => (globalThis.history.length > 1 ? router.back() : router.push(parentPath))}
        >
          <ArrowLeft className="h-4 w-4" />
        </Button>
      )}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem className="hidden md:block">
            <BreadcrumbLink href="/">Home</BreadcrumbLink>
          </BreadcrumbItem>

          {updatedBreadcrumbs.length > 0 && <CustomBreadcrumbSeparator />}

          {updatedBreadcrumbs.map(crumb => {
            return (
              <React.Fragment key={crumb.href}>
                <BreadcrumbItem className={!crumb.isLast ? 'hidden md:block' : undefined}>
                  <BreadcrumbLink href={crumb.href} aria-current={crumb.isLast ? 'page' : undefined}>
                    {crumb.title}
                  </BreadcrumbLink>
                </BreadcrumbItem>
                {!crumb.isLast && <CustomBreadcrumbSeparator />}
              </React.Fragment>
            )
          })}
        </BreadcrumbList>
      </Breadcrumb>
    </div>
  )
}
