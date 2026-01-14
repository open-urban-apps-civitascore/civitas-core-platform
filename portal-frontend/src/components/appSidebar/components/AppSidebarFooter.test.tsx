import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'
import messages from '@/messages/de.json'

import { AppSidebarFooter } from './AppSidebarFooter'

vi.mock('@/auth', () => ({
  signOut: vi.fn(),
}))

describe('AppSidebarFooter', () => {
  const mockUser = {
    name: 'John Doe',
    email: 'john.doe@example.com',
  }

  const renderWithProviders = (user?: { name: string | null; email: string | null } | null) => {
    return render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <SidebarProvider>
          <AppSidebarFooter user={user} />
        </SidebarProvider>
      </NextIntlClientProvider>,
    )
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('user info display', () => {
    it('renders user name when user provided', () => {
      renderWithProviders(mockUser)

      expect(screen.getByText('John Doe')).toBeInTheDocument()
    })

    it('renders "Guest" when no user provided', () => {
      renderWithProviders(null)

      expect(screen.getByText('Guest')).toBeInTheDocument()
    })

    it('does not render email when null', () => {
      renderWithProviders({ name: mockUser.name, email: null })

      const emailElements = screen.queryAllByText('john.doe@example.com')
      expect(emailElements).toHaveLength(0)
    })

    it('does not render email when empty string', () => {
      renderWithProviders({ name: mockUser.name, email: '' })

      const button = screen.getByRole('button')
      expect(button).not.toHaveTextContent('john.doe@example.com')
    })
  })

  describe('avatar display', () => {
    it('renders avatar fallback with first letter of name', () => {
      renderWithProviders(mockUser)

      expect(screen.getAllByText('J')[0]).toBeInTheDocument()
    })

    it('renders "G" fallback when no user name', () => {
      renderWithProviders(null)

      expect(screen.getAllByText('G')[0]).toBeInTheDocument()
    })
  })

  describe('dropdown trigger', () => {
    it('renders dropdown trigger button', () => {
      renderWithProviders(mockUser)

      const button = screen.getByRole('button')
      expect(button).toBeInTheDocument()
    })

    it('has correct accessibility attributes for dropdown menu', () => {
      renderWithProviders(mockUser)

      const button = screen.getByRole('button')
      expect(button).toHaveAttribute('aria-haspopup', 'menu')
      expect(button).toHaveAttribute('data-state', 'closed')
    })

    it('displays user info in trigger button', () => {
      renderWithProviders(mockUser)

      const button = screen.getByRole('button')
      expect(button).toHaveTextContent('John Doe')
      expect(button).toHaveTextContent('john.doe@example.com')
    })
  })
})
