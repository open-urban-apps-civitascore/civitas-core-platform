import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { SortableTableHeader } from './SortableTableHeader'

const createMockColumn = (isSorted: 'asc' | 'desc' | false = false) => ({
  toggleSorting: vi.fn(),
  getIsSorted: vi.fn(() => isSorted),
})

describe('SortableTableHeader', () => {
  it('renders title when provided', () => {
    const mockColumn = createMockColumn()

    render(<SortableTableHeader column={mockColumn as never} title="Test Title" />)

    expect(screen.getByText('Test Title')).toBeInTheDocument()
  })

  it('renders without title when not provided', () => {
    const mockColumn = createMockColumn()

    render(<SortableTableHeader column={mockColumn as never} />)

    expect(screen.getByRole('button')).toBeInTheDocument()
    expect(screen.queryByText(/./)).toBeNull()
  })

  it('renders the ArrowUpDown icon inside the button', () => {
    const mockColumn = createMockColumn()

    render(<SortableTableHeader column={mockColumn as never} />)

    const button = screen.getByRole('button')
    const svgIcon = button.querySelector('svg')
    expect(svgIcon).toBeInTheDocument()
  })

  it('calls toggleSorting with true when column is sorted ascending', () => {
    const mockColumn = createMockColumn('asc')

    render(<SortableTableHeader column={mockColumn as never} />)

    fireEvent.click(screen.getByRole('button'))

    expect(mockColumn.toggleSorting).toHaveBeenCalledWith(true)
  })

  it('calls toggleSorting with false when column is sorted descending', () => {
    const mockColumn = createMockColumn('desc')

    render(<SortableTableHeader column={mockColumn as never} />)

    fireEvent.click(screen.getByRole('button'))

    expect(mockColumn.toggleSorting).toHaveBeenCalledWith(false)
  })

  it('calls toggleSorting with false when column is not sorted', () => {
    const mockColumn = createMockColumn(false)

    render(<SortableTableHeader column={mockColumn as never} />)

    fireEvent.click(screen.getByRole('button'))

    expect(mockColumn.toggleSorting).toHaveBeenCalledWith(false)
  })

  it('applies custom className to the button', () => {
    const mockColumn = createMockColumn()

    render(<SortableTableHeader column={mockColumn as never} className="custom-class" />)

    const button = screen.getByRole('button')
    expect(button).toHaveClass('custom-class')
  })
})
