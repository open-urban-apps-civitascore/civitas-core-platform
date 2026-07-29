import { useQueries } from '@tanstack/react-query'

import {
  Breadcrumb,
  BreadcrumbApiResponse,
} from '@/components/appHeader/components/breadcrumb-navigation/BreadcrumbNavigation'

import { apiRequest } from '../request/apiRequest'

export const BREADCRUMB_QUERY_KEY = 'breadcrumb'

export const useGetBredcrumbs = (breadcrumbs: Breadcrumb[]) =>
  useQueries({
    queries: breadcrumbs.map(crumb => ({
      queryKey: [BREADCRUMB_QUERY_KEY, crumb.href],
      enabled: crumb.isDynamic && crumb.apiHref !== undefined,
      queryFn: () =>
        apiRequest<BreadcrumbApiResponse>({
          endpoint: crumb.apiHref ?? crumb.href,
          method: 'GET',
          headers: { 'x-api-request': 'true' },
          errorMessage: 'An error occurred while fetching breadcrumbs data.',
        }),
    })),
  })
