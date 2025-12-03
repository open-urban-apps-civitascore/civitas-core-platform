import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ExpanderCell } from './ExpanderCell'

const createMockRow = (options: { canExpand?: boolean; isExpanded?: boolean; depth?: number } = {}) => ({
  getCanExpand: vi.fn(() => options.canExpand ?? true),
  getIsExpanded: vi.fn(() => options.isExpanded ?? false),
  toggleExpanded: vi.fn(),
  depth: options.depth ?? 0,
})

describe('ExpanderCell', () => {
  it('renders value text when row can expand', () => {
    const mockRow = createMockRow({ canExpand: true })

    render(<ExpanderCell row={mockRow as never} value="Test Value" />)

    expect(screen.getByText('Test Value')).toBeInTheDocument()
  })

  it('renders value text when row cannot expand', () => {
    const mockRow = createMockRow({ canExpand: false })

    render(<ExpanderCell row={mockRow as never} value="Non-expandable Value" />)

    expect(screen.getByText('Non-expandable Value')).toBeInTheDocument()
  })

  it('shows ChevronDown icon when row is expanded', () => {
    const mockRow = createMockRow({ canExpand: true, isExpanded: true })

    render(<ExpanderCell row={mockRow as never} value="Test" />)

    const button = screen.getByRole('button')
    const svgIcon = button.querySelector('svg')
    expect(svgIcon).toBeInTheDocument()
    expect(svgIcon).toHaveClass('lucide-chevron-down')
  })

  it('shows ChevronRight icon when row is not expanded', () => {
    const mockRow = createMockRow({ canExpand: true, isExpanded: false })

    render(<ExpanderCell row={mockRow as never} value="Test" />)

    const button = screen.getByRole('button')
    const svgIcon = button.querySelector('svg')
    expect(svgIcon).toBeInTheDocument()
    expect(svgIcon).toHaveClass('lucide-chevron-right')
  })

  it('calls row.toggleExpanded() when button is clicked', () => {
    const mockRow = createMockRow({ canExpand: true })

    render(<ExpanderCell row={mockRow as never} value="Test" />)

    fireEvent.click(screen.getByRole('button'))

    expect(mockRow.toggleExpanded).toHaveBeenCalled()
  })

  it('stops event propagation when button is clicked', () => {
    const mockRow = createMockRow({ canExpand: true })
    const parentClickHandler = vi.fn()

    render(
      <div onClick={parentClickHandler}>
        <ExpanderCell row={mockRow as never} value="Test" />
      </div>,
    )

    fireEvent.click(screen.getByRole('button'))

    expect(parentClickHandler).not.toHaveBeenCalled()
  })

  it('does not render expand button when row cannot expand', () => {
    const mockRow = createMockRow({ canExpand: false })

    render(<ExpanderCell row={mockRow as never} value="Test" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('applies custom className to container', () => {
    const mockRow = createMockRow()

    const { container } = render(<ExpanderCell row={mockRow as never} value="Test" className="custom-class" />)

    const containerDiv = container.firstChild as HTMLElement
    expect(containerDiv).toHaveClass('custom-class')
  })
})
