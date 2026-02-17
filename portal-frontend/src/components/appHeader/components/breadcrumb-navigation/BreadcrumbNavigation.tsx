'use client'

import { Slash } from 'lucide-react'
import { useParams, usePathname } from 'next/navigation'
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
  const t = useTranslations('sidebar')

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

  if (!pathname) return null

  return (
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
  )
}
