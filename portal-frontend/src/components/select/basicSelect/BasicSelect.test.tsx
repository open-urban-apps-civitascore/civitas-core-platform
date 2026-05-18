import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { BasicSelect } from './BasicSelect'

describe('BasicSelect', () => {
  const mockOptions = [
    { value: 'option1', label: 'Option 1' },
    { value: 'option2', label: 'Option 2' },
    { value: 'option3', label: 'Option 3' },
  ]

  it('renders with placeholder', () => {
    const onValueChange = vi.fn()
    render(<BasicSelect onValueChange={onValueChange} options={mockOptions} placeholder="Select an option" />)

    expect(screen.getByText('Select an option')).toBeInTheDocument()
  })

  it('calls onValueChange when option is selected', () => {
    const onValueChange = vi.fn()
    render(<BasicSelect onValueChange={onValueChange} options={mockOptions} />)

    const trigger = screen.getByRole('combobox')
    fireEvent.click(trigger)

    const option = screen.getByText('Option 2')
    fireEvent.click(option)

    expect(onValueChange).toHaveBeenCalledWith('option2')
  })

  it('renders all options when opened', () => {
    const onValueChange = vi.fn()
    render(<BasicSelect onValueChange={onValueChange} options={mockOptions} />)

    const trigger = screen.getByRole('combobox')
    fireEvent.click(trigger)

    expect(screen.getByText('Option 1')).toBeInTheDocument()
    expect(screen.getByText('Option 2')).toBeInTheDocument()
    expect(screen.getByText('Option 3')).toBeInTheDocument()
  })

  it('renders with defaultValue', () => {
    const onValueChange = vi.fn()
    render(<BasicSelect onValueChange={onValueChange} options={mockOptions} value="option2" />)

    expect(screen.getByText('Option 2')).toBeInTheDocument()
  })

  it('applies custom className to trigger', () => {
    const onValueChange = vi.fn()
    const { container } = render(
      <BasicSelect onValueChange={onValueChange} options={mockOptions} triggerClassName="custom-trigger" />,
    )

    const trigger = container.querySelector('.custom-trigger')
    expect(trigger).toBeInTheDocument()
  })
})
