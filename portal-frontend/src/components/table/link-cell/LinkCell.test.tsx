import { fireEvent, render, screen } from '@testing-library/react'
import React from 'react'
import { vi } from 'vitest'

const mockUseIsTruncated = vi.fn().mockReturnValue(false)
const TooltipContext = React.createContext(false)

vi.mock('@/hooks/use-is-truncated', () => ({
  useIsTruncated: () => mockUseIsTruncated(),
}))
vi.mock('../../ui/tooltip', () => ({
  // eslint-disable-next-line react/boolean-prop-naming
  Tooltip: ({ children, open }: { children: React.ReactNode; open?: boolean }) => (
    <TooltipContext.Provider value={open !== false}>{children}</TooltipContext.Provider>
  ),
  TooltipTrigger: ({ children }: { children: React.ReactNode }) => children,
  TooltipContent: ({ children }: { children: React.ReactNode }) => {
    const isOpen = React.useContext(TooltipContext)
    return isOpen ? <div role="tooltip">{children}</div> : null
  },
}))
vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: false,
    requestNavigation: vi.fn(),
  }),
}))

import { LinkCell } from './LinkCell'

describe('LinkCell', () => {
  beforeEach(() => {
    mockUseIsTruncated.mockReturnValue(false)
  })

  it('renders a link with correct href and children', () => {
    render(<LinkCell href="/test-link">Test Link</LinkCell>)
    const link = screen.getByRole('link')
    expect(link).toHaveTextContent('Test Link')
    expect(link).toHaveAttribute('href', '/test-link')
  })

  it('has underline on hover', () => {
    render(<LinkCell href="/test-link">Test Link</LinkCell>)
    const link = screen.getByRole('link')
    expect(link).toHaveClass('hover:underline')
  })

  it('renders JSX children correctly', () => {
    render(
      <LinkCell href="/test">
        <span data-testid="custom-child">Custom Content</span>
      </LinkCell>,
    )
    expect(screen.getByTestId('custom-child')).toBeInTheDocument()
  })

  it('renders plain text when isDisabled is true', () => {
    render(
      <LinkCell href="/test" isDisabled>
        Disabled Link
      </LinkCell>,
    )
    expect(screen.queryByRole('link', { name: 'Disabled Link' })).not.toBeInTheDocument()
    expect(screen.getByText('Disabled Link')).toBeInTheDocument()
  })

  it('renders a link when isDisabled is false', () => {
    render(
      <LinkCell href="/test" isDisabled={false}>
        Enabled Link
      </LinkCell>,
    )
    expect(screen.getByRole('link', { name: /Enabled Link/ })).toHaveAttribute('href', '/test')
  })

  it('does not show tooltip when text is not truncated', async () => {
    vi.useFakeTimers()
    render(<LinkCell href="/test-link">Test Link</LinkCell>)
    fireEvent.mouseOver(screen.getByRole('link'))
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    vi.useRealTimers()
  })

  it('shows tooltip when text is truncated', async () => {
    vi.useFakeTimers()
    mockUseIsTruncated.mockReturnValue(true)
    render(<LinkCell href="/test-link">Test Link</LinkCell>)
    fireEvent.mouseOver(screen.getByRole('link'))
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Test Link')
    vi.useRealTimers()
  })
})
