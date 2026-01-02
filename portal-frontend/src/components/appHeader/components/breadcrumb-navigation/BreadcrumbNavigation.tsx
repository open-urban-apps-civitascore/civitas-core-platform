'use client'

import { Slash } from 'lucide-react'
import { useParams, usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import React, { useEffect } from 'react'

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'

const getName = (firstName?: string, lastName?: string) => (firstName && lastName ? `${firstName} ${lastName}` : null)

type Crumb = {
  title: string
  href: string
  isLast: boolean
  isDynamic: boolean
}

export const BreadcrumbNavigation = () => {
  const pathname = usePathname()
  const params = useParams()
  const t = useTranslations('sidebar')
  const [breadcrumbs, setBreadcrumbs] = React.useState<Crumb[]>([])

  useEffect(() => {
    if (!pathname) return

    const segments = pathname.split('/').filter(Boolean)

    const baseBreadCrumbs = segments.map((segment, index) => ({
      title: segment,
      value: segment,
      href: `/${segments.slice(0, index + 1).join('/')}`,
      isLast: index === segments.length - 1,
      isDynamic: Object.values(params).includes(segment),
    }))

    const loadTitles = async () => {
      const updated = await Promise.all(
        baseBreadCrumbs.map(async crumb => {
          if (!crumb.isDynamic) return crumb

          const res = await fetch(`/api${crumb.href}`)
          const data = await res.json()

          return {
            ...crumb,
            title: data.name || getName(data.firstName, data.lastName) || data.title || crumb.title,
          }
        }),
      )
      setBreadcrumbs(updated)
    }

    if (baseBreadCrumbs.some(c => c.isDynamic)) {
      loadTitles()
    } else {
      setBreadcrumbs(baseBreadCrumbs)
    }
  }, [pathname, params])

  const CustomBreadcrumbSeparator = () => (
    <BreadcrumbSeparator aria-hidden className="hidden md:block">
      <Slash />
    </BreadcrumbSeparator>
  )

  return (
    <Breadcrumb>
      <BreadcrumbList>
        <BreadcrumbItem className="hidden md:block">
          <BreadcrumbLink href="/">Home</BreadcrumbLink>
        </BreadcrumbItem>

        {breadcrumbs.length > 0 && <CustomBreadcrumbSeparator />}

        {breadcrumbs.map(segment => {
          const isLast = segment.isLast
          const displayTitle = segment.isDynamic ? segment.title : t(segment.title)

          return (
            <React.Fragment key={`${segment.href}`}>
              <BreadcrumbItem className={!isLast ? 'hidden md:block' : undefined}>
                <BreadcrumbLink href={segment.href} aria-current={isLast ? 'page' : undefined}>
                  {displayTitle}
                </BreadcrumbLink>
              </BreadcrumbItem>
              {!isLast && <CustomBreadcrumbSeparator />}
            </React.Fragment>
          )
        })}
      </BreadcrumbList>
    </Breadcrumb>
  )
}
