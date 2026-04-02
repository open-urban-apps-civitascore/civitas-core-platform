'use client'

import { isServer, QueryClient, QueryClientProvider } from '@tanstack/react-query'

const makeQueryClient = () => {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
      },
    },
  })
}

let browserQueryClient: QueryClient | undefined = undefined

const getQueryClient = () => {
  if (isServer) {
    // Server: always make a new query client
    return makeQueryClient()
  } else {
    // Browser: make a new query client if not yet existing
    if (!browserQueryClient) browserQueryClient = makeQueryClient()
    return browserQueryClient
  }
}
const QueryProvider = ({ children }: { children: React.ReactNode }) => {
  // Avoid useState when initializing the query client if there is no suspense boundary
  const queryClient = getQueryClient()

  if (typeof globalThis !== 'undefined') {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(globalThis as any).__TANSTACK_QUERY_CLIENT__ = queryClient
  }

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

export default QueryProvider
