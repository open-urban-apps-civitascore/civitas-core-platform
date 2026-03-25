import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

import { apiRequest } from '@/app/services/api/request/apiRequest'

import { BreadcrumbNavigation } from './BreadcrumbNavigation'

const mockPush = vi.fn()
const mockUsePathname = vi.fn().mockReturnValue('/admin/users/123')
const mockUseParams = vi.fn().mockReturnValue({ id: '123' })

vi.mock('next/navigation', async () => ({
  usePathname: () => mockUsePathname(),
  useParams: () => mockUseParams(),
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const mockUseIsTruncated = vi.fn().mockReturnValue(false)

vi.mock('@/hooks/use-is-truncated', () => ({
  useIsTruncated: () => mockUseIsTruncated(),
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
    mockUsePathname.mockReturnValue('/admin/users/123')
    mockUseParams.mockReturnValue({ id: '123' })
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

  it('uses datastructure version endpoint for version breadcrumbs', async () => {
    mockUsePathname.mockReturnValue('/datastructures/ds-1/version-2')
    mockUseParams.mockReturnValue({ datastructureId: 'ds-1', versionId: 'version-2' })

    vi.mocked(apiRequest)
      .mockResolvedValueOnce({ data: { name: 'Datastructure A' } })
      .mockResolvedValueOnce({ data: { version: '2' } })

    renderWithClient()

    await waitFor(() => {
      expect(apiRequest).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining({
          endpoint: '/datastructures/ds-1/versions/version-2',
          method: 'GET',
        }),
      )
      expect(screen.getByText('Version 2')).toBeInTheDocument()
    })
  })

  it('does not show tooltip when dynamic breadcrumb is not truncated', async () => {
    vi.useFakeTimers()
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: { name: 'Datastructure A' },
    })
    renderWithClient()

    await vi.waitFor(() => {
      expect(screen.getByText('Datastructure A')).toBeInTheDocument()
    })
    fireEvent.focus(screen.getByText('Datastructure A'))
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    vi.useRealTimers()
  })

  it('shows tooltip when dynamic breadcrumb is truncated', async () => {
    vi.useFakeTimers()
    mockUseIsTruncated.mockReturnValue(true)
    vi.mocked(apiRequest).mockResolvedValueOnce({
      data: { name: 'Datastructure with a long name' },
    })
    renderWithClient()

    await vi.waitFor(() => {
      expect(screen.getByText('Datastructure with a long name')).toBeInTheDocument()
    })
    fireEvent.focus(screen.getByText('Datastructure with a long name'))
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Datastructure with a long name')
    vi.useRealTimers()
    mockUseIsTruncated.mockReturnValue(false)
  })
})
