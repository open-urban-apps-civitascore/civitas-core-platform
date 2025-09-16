'use client'

import { Slash } from 'lucide-react'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'
import React, { useMemo } from 'react'

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'

export const BreadcrumbNavigation = () => {
  const pathname = usePathname()
  const t = useTranslations('sidebar')

  const breadcrumbs = useMemo(() => {
    return pathname ? pathname.split('/').filter(segment => segment !== '') : []
  }, [pathname])

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

        {breadcrumbs.map((segment, index) => {
          const href = `/${breadcrumbs.slice(0, index + 1).join('/')}`
          const isLast = index === breadcrumbs.length - 1

          return (
            <React.Fragment key={segment}>
              <BreadcrumbItem className={!isLast ? 'hidden md:block' : undefined}>
                <BreadcrumbLink href={href} aria-current={isLast ? 'page' : undefined}>
                  {t(segment)}
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
