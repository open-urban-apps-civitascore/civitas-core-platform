import { render, screen } from '@testing-library/react'

import { ActivityBadge } from './ActivityBadge'

describe('ActivityBadge', () => {
  it('renders title', () => {
    render(<ActivityBadge title="Active" isActive />)
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('applies green styles when active', () => {
    render(<ActivityBadge title="Active" isActive />)
    const badge = screen.getByText('Active')

    expect(badge.className).toContain('bg-green-600/10')
    expect(badge.className).toContain('text-green-800')
  })

  it('applies red styles when inactive', () => {
    render(<ActivityBadge title="Inactive" isActive={false} />)
    const badge = screen.getByText('Inactive')

    expect(badge.className).toContain('bg-red-600/10')
    expect(badge.className).toContain('text-red-700')
  })

  it('applies default styles when isActive is undefined', () => {
    render(<ActivityBadge title="Unknown" isActive={undefined} />)
    const badge = screen.getByText('Unknown')

    expect(badge.className).toContain('bg-secondary')
    expect(badge.className).toContain('text-foreground')
  })
})
