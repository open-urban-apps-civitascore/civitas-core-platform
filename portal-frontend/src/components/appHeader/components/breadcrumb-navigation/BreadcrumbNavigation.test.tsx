import { render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

import { BreadcrumbNavigation } from './BreadcrumbNavigation'

vi.mock('next/navigation', async () => ({
  usePathname: vi.fn().mockReturnValue('/admin/users/123'),
  useParams: vi.fn().mockReturnValue({ id: '123' }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

describe('BreadcrumbNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders Home Breadcrumb', () => {
    render(<BreadcrumbNavigation />)

    expect(screen.getByText('Home')).toBeInTheDocument()
  })

  it('renders static breadcrumbs', async () => {
    render(<BreadcrumbNavigation />)

    await waitFor(async () => {
      expect(await screen.findByText('admin')).toBeInTheDocument()
      expect(await screen.findByText('users')).toBeInTheDocument()
    })
  })

  it("loads dynamic segment's name from api", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ firstName: 'Max', lastName: 'Mustermann' }),
    })

    render(<BreadcrumbNavigation />)

    await waitFor(() => {
      expect(screen.getByText('Max Mustermann')).toBeInTheDocument()
    })

    expect(fetch).toHaveBeenCalledWith('/api/admin/users/123')
  })

  it('Shows dynamic segment string if loading segment name fails', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({}),
    })

    render(<BreadcrumbNavigation />)

    await waitFor(() => {
      expect(screen.getByText('123')).toBeInTheDocument()
    })
  })

  it('sets aria-current only for the last segment', async () => {
    render(<BreadcrumbNavigation />)

    await waitFor(() => {
      const current = screen.getByText('123')
      expect(current).toHaveAttribute('aria-current', 'page')
    })
  })
})
