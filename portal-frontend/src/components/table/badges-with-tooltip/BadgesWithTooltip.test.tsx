import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { BadgesWithTooltip } from './BadgesWithTooltip'

describe('BadgesWithTooltip', () => {
  it('renders only visible badges when items length equals minVisibleBadges', () => {
    render(<BadgesWithTooltip items={['A']} minVisibleBadges={1} />)

    expect(screen.getByText('A')).toBeInTheDocument()
    expect(screen.queryByText('+')).not.toBeInTheDocument()
  })

  it('renders correct number of visible badges', () => {
    const { container } = render(<BadgesWithTooltip items={['A', 'B', 'C', 'D']} minVisibleBadges={2} />)

    const badges = container.querySelectorAll('[data-slot="badge"]')
    expect(badges).toHaveLength(2)
    expect(screen.getByText('A')).toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
  })

  it('renders counter badge with correct number when more items than minVisibleBadges exist', () => {
    render(<BadgesWithTooltip items={['A', 'B', 'C']} minVisibleBadges={1} />)

    expect(screen.getByText('A')).toBeInTheDocument()
    expect(screen.getByText('+2')).toBeInTheDocument()
  })

  it('does not render counter when minVisibleBadges >= items length', () => {
    render(<BadgesWithTooltip items={['A', 'B']} minVisibleBadges={2} />)

    expect(screen.getByText('A')).toBeInTheDocument()
    expect(screen.getByText('B')).toBeInTheDocument()
    expect(screen.queryByText('+')).not.toBeInTheDocument()
  })

  it('renders "-" when items is empty', () => {
    render(<BadgesWithTooltip items={[]} />)
    expect(screen.getByText('-')).toBeInTheDocument()
  })
})
