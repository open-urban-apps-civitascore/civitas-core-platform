import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

import { apiRequest } from '@/app/services/api/request/apiRequest'

import { BreadcrumbNavigation } from './BreadcrumbNavigation'

const mockPush = vi.fn()
vi.mock('next/navigation', async () => ({
  usePathname: vi.fn().mockReturnValue('/admin/users/123'),
  useParams: vi.fn().mockReturnValue({ id: '123' }),
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false },
  },
})
const renderWithClient = () => {
  return render(
    <QueryClientProvider client={queryClient}>
      <BreadcrumbNavigation />
    </QueryClientProvider>,
  )
}

describe('BreadcrumbNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    queryClient.clear()
  })

  it('renders Home Breadcrumb', () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: {},
    })
    renderWithClient()

    expect(screen.getByText('Home')).toBeInTheDocument()
  })

  it('renders static breadcrumbs', async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: {},
    })
    renderWithClient()

    await waitFor(async () => {
      expect(await screen.findByText('admin')).toBeInTheDocument()
      expect(await screen.findByText('users')).toBeInTheDocument()
    })
  })

  it("loads dynamic segment's name from api", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: { firstName: 'Max', lastName: 'Mustermann' },
    })

    renderWithClient()

    await waitFor(() => {
      expect(screen.getByText('Max Mustermann')).toBeInTheDocument()
    })

    expect(apiRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        endpoint: '/admin/users/123',
        method: 'GET',
      }),
    )
  })

  it('Shows dynamic segment string if loading segment name fails', async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: {},
    })
    renderWithClient()

    await waitFor(() => {
      expect(screen.getByText('123')).toBeInTheDocument()
    })
  })

  it('sets aria-current only for the last segment', async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: {},
    })
    renderWithClient()

    await waitFor(() => {
      const current = screen.getByText('123')
      expect(current).toHaveAttribute('aria-current', 'page')
    })
  })
})
