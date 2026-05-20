import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { GroupedSelect } from './GroupedSelect'

const mockGroupedOptions = [
  {
    label: 'Group A',
    options: [
      { value: 'a1', label: 'Option A1' },
      { value: 'a2', label: 'Option A2' },
    ],
  },
  {
    label: 'Group B',
    options: [
      { value: 'b1', label: 'Option B1' },
      { value: 'b2', label: 'Option B2' },
    ],
  },
]

describe('GroupedSelect', () => {
  it('renders with placeholder', () => {
    render(<GroupedSelect groupedOptions={mockGroupedOptions} onValueChange={vi.fn()} placeholder="Select an option" />)

    expect(screen.getByText('Select an option')).toBeDefined()
  })

  it('renders group labels and all options when opened', () => {
    render(<GroupedSelect groupedOptions={mockGroupedOptions} onValueChange={vi.fn()} />)

    fireEvent.click(screen.getByRole('combobox'))

    expect(screen.getByText('Group A')).toBeDefined()
    expect(screen.getByText('Group B')).toBeDefined()
    expect(screen.getByText('Option A1')).toBeDefined()
    expect(screen.getByText('Option A2')).toBeDefined()
    expect(screen.getByText('Option B1')).toBeDefined()
    expect(screen.getByText('Option B2')).toBeDefined()
  })

  it('calls onValueChange with the selected value', () => {
    const onValueChange = vi.fn()
    render(<GroupedSelect groupedOptions={mockGroupedOptions} onValueChange={onValueChange} />)

    fireEvent.click(screen.getByRole('combobox'))
    fireEvent.click(screen.getByText('Option B1'))

    expect(onValueChange).toHaveBeenCalledWith('b1')
  })

  it('displays the pre-selected value', () => {
    render(<GroupedSelect groupedOptions={mockGroupedOptions} onValueChange={vi.fn()} value="a2" />)

    expect(screen.getByText('Option A2')).toBeDefined()
  })

  it('renders as disabled', () => {
    render(<GroupedSelect groupedOptions={mockGroupedOptions} onValueChange={vi.fn()} disabled />)

    expect((screen.getByRole('combobox') as HTMLButtonElement).disabled).toBe(true)
  })

  it('applies triggerClassName to the trigger', () => {
    const { container } = render(
      <GroupedSelect groupedOptions={mockGroupedOptions} onValueChange={vi.fn()} triggerClassName="custom-trigger" />,
    )

    expect(container.querySelector('.custom-trigger')).toBeDefined()
  })
})
