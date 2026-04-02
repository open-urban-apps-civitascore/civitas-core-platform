import { useQueries } from '@tanstack/react-query'

import {
  Breadcrumb,
  BreadcrumbApiResponse,
} from '@/components/appHeader/components/breadcrumb-navigation/BreadcrumbNavigation'

import { apiRequest } from '../request/apiRequest'

export const useGetBredcrumbs = (breadcrumbs: Breadcrumb[]) =>
  useQueries({
    queries: breadcrumbs.map(crumb => ({
      queryKey: ['breadcrumb', crumb.href],
      enabled: crumb.isDynamic && !!crumb.href,
      queryFn: () =>
        apiRequest<BreadcrumbApiResponse>({
          endpoint: crumb.apiHref ?? crumb.href,
          method: 'GET',
          headers: { 'x-api-request': 'true' },
          errorMessage: 'An error occurred while fetching breadcrumbs data.',
        }),
    })),
  })
