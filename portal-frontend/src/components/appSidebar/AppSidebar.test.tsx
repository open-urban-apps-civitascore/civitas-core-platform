import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'
import messages from '@/messages/de.json'

// Mock the async components
vi.mock('./components/AppSidebarContent', () => ({
  AppSidebarContent: () => <div data-slot="sidebar-content" data-testid="sidebar-content" />,
}))

// Mock auth for AppSidebarFooter which imports signOut
const { mockSignOut } = vi.hoisted(() => ({
  mockSignOut: vi.fn(),
}))
vi.mock('@/auth', () => ({
  signOut: mockSignOut,
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

    it('renders AppSidebarHeader with tenant name and CIVITAS/CORE', async () => {
      await renderWithProviders(mockUser)

      // Tenant name comes from NEXT_PUBLIC_TENANT_NAME env var (falls back to 'Mandanten-Name')
      expect(screen.getByText('Mandanten-Name')).toBeInTheDocument()
      expect(screen.getByText('CIVITAS/CORE')).toBeInTheDocument()
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

  describe('dropdown menu', () => {
    const openDropdownMenu = async () => {
      const dropdownTrigger = screen.getByRole('button', { name: /John Doe/i })
      fireEvent.pointerDown(dropdownTrigger, { button: 0, ctrlKey: false })

      // Wait for dropdown to appear
      await waitFor(() => {
        expect(dropdownTrigger).toHaveAttribute('data-state', 'open')
      })
    }

    it('shows user name and email in dropdown content when opened', async () => {
      await renderWithProviders(mockUser)

      await openDropdownMenu()

      const userNames = screen.getAllByText('John Doe')
      const userEmails = screen.getAllByText('john.doe@example.com')

      expect(userNames).toHaveLength(2)
      expect(userEmails).toHaveLength(2)
    })

    it('shows logout button in dropdown menu', async () => {
      await renderWithProviders(mockUser)

      await openDropdownMenu()

      expect(screen.getByRole('menuitem', { name: /Abmelden/i })).toBeInTheDocument()
    })

    it('triggers signOut when logout button is clicked', async () => {
      await renderWithProviders(mockUser)

      await openDropdownMenu()

      // Click the logout button
      const logoutButton = screen.getByRole('menuitem', { name: /Abmelden/i })
      fireEvent.click(logoutButton)

      // signOut should have been called
      expect(mockSignOut).toHaveBeenCalledWith({ redirectTo: '/login' })
    })
  })
})
