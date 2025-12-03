import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'
import messages from '@/messages/de.json'

// Mock the async components
vi.mock('./components/AppSidebarContent', () => ({
  AppSidebarContent: () => <div data-slot="sidebar-content" data-testid="sidebar-content" />,
}))

// Mock auth for AppSidebarFooter which imports signOut
vi.mock('@/auth', () => ({
  signOut: vi.fn(),
}))

// Import after mocks
import { AppSidebar } from './AppSidebar'

describe('AppSidebar', () => {
  const mockUser = {
    name: 'John Doe',
    email: 'john.doe@example.com',
  }

  const renderWithProviders = async (user?: { name: string | null; email: string | null } | null) => {
    const Component = await AppSidebar({ user })
    return render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <SidebarProvider>{Component}</SidebarProvider>
      </NextIntlClientProvider>,
    )
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('structure', () => {
    it('renders navigation landmark with correct aria-label', async () => {
      await renderWithProviders(mockUser)

      const nav = screen.getByRole('navigation')
      expect(nav).toBeInTheDocument()
      expect(nav).toHaveAttribute('aria-label', 'Main navigation')
    })

    it('renders AppSidebarContent component', async () => {
      await renderWithProviders(mockUser)

      expect(screen.getByTestId('sidebar-content')).toBeInTheDocument()
    })

    it('renders AppSidebarHeader with organization info', async () => {
      await renderWithProviders(mockUser)

      // Organization 1 comes from the mock data (currentOrganization)
      expect(screen.getByText('Organization 1')).toBeInTheDocument()
      expect(screen.getByText('Tenant A')).toBeInTheDocument()
    })

    it('renders AppSidebarFooter with user info', async () => {
      await renderWithProviders(mockUser)

      expect(screen.getByText('John Doe')).toBeInTheDocument()
      expect(screen.getByText('john.doe@example.com')).toBeInTheDocument()
    })
  })

  describe('user prop handling', () => {
    it('handles null user prop by displaying Guest', async () => {
      await renderWithProviders(null)

      expect(screen.getByText('Guest')).toBeInTheDocument()
    })

    it('handles undefined user prop by displaying Guest', async () => {
      await renderWithProviders(undefined)

      expect(screen.getByText('Guest')).toBeInTheDocument()
    })
  })
})
