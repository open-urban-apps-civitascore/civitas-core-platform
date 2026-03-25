'use client'

import { ArrowLeft, Slash } from 'lucide-react'
import { useParams, usePathname, useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import React, { useRef } from 'react'

import { useGetBredcrumbs } from '@/app/services/api/breadcrumbs/clientRequests'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useIsTruncated } from '@/hooks/use-is-truncated'
import { cn } from '@/lib/utils'

export type Breadcrumb = {
  title: string
  href: string
  apiHref?: string
  isLast: boolean
  isDynamic: boolean
}

export type BreadcrumbApiResponse = {
  name?: string
  firstName?: string
  lastName?: string
  title?: string
  version?: string
}

const BreadcrumbLinkWithTooltip = ({ href, title, isLast }: { href: string; title: string; isLast: boolean }) => {
  const ref = useRef<HTMLAnchorElement>(null)
  const isTruncated = useIsTruncated(ref)

  return (
    <Tooltip open={isTruncated ? undefined : false}>
      <TooltipTrigger asChild>
        <BreadcrumbLink ref={ref} href={href} aria-current={isLast ? 'page' : undefined} className="truncate block">
          {title}
        </BreadcrumbLink>
      </TooltipTrigger>
      <TooltipContent variant="secondary">{title}</TooltipContent>
    </Tooltip>
  )
}

const getName = (firstName?: string, lastName?: string) => (firstName && lastName ? `${firstName} ${lastName}` : null)

const isDatastructureVersionBreadcrumb = (segments: string[], index: number) =>
  segments[0] === 'datastructures' && index === 2

const getBreadcrumbApiHref = (segments: string[], index: number) => {
  const href = `/${segments.slice(0, index + 1).join('/')}`

  if (isDatastructureVersionBreadcrumb(segments, index)) {
    const [, datastructureId, versionId] = segments
    return `/datastructures/${datastructureId}/versions/${versionId}`
  }

  return href
}

const getTitle = (
  breadcrumb: Breadcrumb,
  data: BreadcrumbApiResponse | undefined,
  t: ReturnType<typeof useTranslations>,
  isDatastructureVersion: boolean,
) => {
  if (!breadcrumb.isDynamic) {
    return t(breadcrumb.title)
  }

  if (isDatastructureVersion && data?.version) {
    return `Version ${data.version}`
  }

  return data?.name || getName(data?.firstName, data?.lastName) || data?.title || breadcrumb.title
}

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
    apiHref: getBreadcrumbApiHref(segments, index),
    isLast: index === segments.length - 1,
    isDynamic: Object.values(params).includes(segment),
  }))

  const results = useGetBredcrumbs(breadcrumbs)

  const updatedBreadcrumbs = breadcrumbs.map((crumb, index) => {
    const data = results[index]?.data?.data
    const title = getTitle(crumb, data, t, isDatastructureVersionBreadcrumb(segments, index))
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
    <div className="flex items-center gap-4 min-w-0">
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
      <Breadcrumb className="min-w-0 ">
        <BreadcrumbList className="flex-nowrap overflow-hidden">
          <BreadcrumbItem className="hidden md:block">
            <BreadcrumbLink href="/">Home</BreadcrumbLink>
          </BreadcrumbItem>

          {updatedBreadcrumbs.length > 0 && <CustomBreadcrumbSeparator />}

          {updatedBreadcrumbs.map(crumb => {
            return (
              <React.Fragment key={crumb.href}>
                <BreadcrumbItem
                  className={cn('min-w-0', !crumb.isLast ? 'hidden md:block' : '', !crumb.isDynamic && 'shrink-0')}
                >
                  <BreadcrumbLinkWithTooltip href={crumb.href} title={crumb.title} isLast={crumb.isLast} />
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
