import { fireEvent, render, screen } from '@testing-library/react'
import React, { RefObject } from 'react'
import { vi } from 'vitest'

const mockUseIsTruncated = vi.fn<(ref: RefObject<HTMLElement | null>) => boolean>().mockReturnValue(false)
const TooltipContext = React.createContext(false)

vi.mock('@/hooks/use-is-truncated', () => ({
  useIsTruncated: (ref: RefObject<HTMLElement | null>) => mockUseIsTruncated(ref),
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

  it('renders the suffix inside the link', () => {
    render(
      <LinkCell href="/test-link" suffix={<span data-testid="suffix">3</span>}>
        Test Link
      </LinkCell>,
    )
    expect(screen.getByRole('link')).toContainElement(screen.getByTestId('suffix'))
  })

  it('keeps only the text in the truncated element when a suffix is set', () => {
    render(
      <LinkCell href="/test-link" suffix={<span data-testid="suffix">3</span>}>
        Test Link
      </LinkCell>,
    )
    const truncatedElement = screen.getByRole('link').querySelector('.truncate')
    expect(truncatedElement).toHaveTextContent(/^Test Link$/)
    expect(truncatedElement).not.toContainElement(screen.getByTestId('suffix'))
  })

  it.each([
    ['with', <span key="suffix">3</span>],
    ['without', undefined],
  ])('measures the truncated element %s a suffix', (_, suffix) => {
    render(
      <LinkCell href="/test-link" suffix={suffix}>
        Test Link
      </LinkCell>,
    )
    const truncatedElement = screen.getByRole('link').querySelector('.truncate')
    expect(truncatedElement).not.toBeNull()
    expect(mockUseIsTruncated.mock.lastCall?.[0].current).toBe(truncatedElement)
  })

  it('renders no extra wrapper without a suffix', () => {
    render(<LinkCell href="/test-link">Test Link</LinkCell>)
    const link = screen.getByRole('link')
    expect(link.children).toHaveLength(2)
    expect(link.children[0]).toHaveClass('flex-1', 'truncate')
    expect(link.children[0]).toHaveTextContent(/^Test Link$/)
  })

  it.each([
    ['false', false],
    ['an empty string', ''],
  ])('renders no extra wrapper for a suffix of %s', (_, suffix) => {
    render(
      <LinkCell href="/test-link" suffix={suffix}>
        Test Link
      </LinkCell>,
    )
    expect(screen.getByRole('link').children[0]).toHaveClass('flex-1', 'truncate')
  })

  it('renders a suffix of zero', () => {
    render(
      <LinkCell href="/test-link" suffix={0}>
        Test Link
      </LinkCell>,
    )
    expect(screen.getByRole('link')).toHaveTextContent(/^Test Link0$/)
  })

  it('renders the suffix next to the text when isDisabled is true', () => {
    render(
      <LinkCell href="/test-link" isDisabled suffix={<span data-testid="suffix">3</span>}>
        Disabled Link
      </LinkCell>,
    )
    expect(screen.getByTestId('suffix')).toBeInTheDocument()
  })

  it('shows only the text in the tooltip when a suffix is set', async () => {
    vi.useFakeTimers()
    mockUseIsTruncated.mockReturnValue(true)
    render(
      <LinkCell href="/test-link" suffix={<span data-testid="suffix">3</span>}>
        Test Link
      </LinkCell>,
    )
    fireEvent.mouseOver(screen.getByRole('link'))
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.getByRole('tooltip')).toHaveTextContent(/^Test Link$/)
    vi.useRealTimers()
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
