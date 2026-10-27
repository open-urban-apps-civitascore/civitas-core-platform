import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { STATUS_TYPES } from '@/types/common'

import { StatusDropdown } from './StatusDropdown'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const defaultProps = {
  status: STATUS_TYPES.DRAFT,
  onStatusChange: vi.fn(),
  statusOptions: [STATUS_TYPES.DRAFT, STATUS_TYPES.AVAILABLE] as const,
  canStage: true,
  canRelease: true,
  isReadOnly: false,
}

const openDropdown = async () => {
  const trigger = screen.getByTestId('statusDropdown')
  fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false })
  await waitFor(() => {
    expect(trigger).toHaveAttribute('data-state', 'open')
  })
}

describe('StatusDropdown', () => {
  it('enables Available option when canRelease is true', async () => {
    render(<StatusDropdown {...defaultProps} canRelease={true} canStage={true} />)

    await openDropdown()

    const availableOption = screen.getByTestId('statusOption-available')
    expect(availableOption).not.toHaveAttribute('data-disabled')
  })

  it('disables Available option when canRelease is false', async () => {
    render(<StatusDropdown {...defaultProps} canRelease={false} canStage={true} />)

    await openDropdown()

    const availableOption = screen.getByTestId('statusOption-available')
    expect(availableOption).toHaveAttribute('data-disabled')
  })

  it('disables Available option when canStage is false', async () => {
    render(<StatusDropdown {...defaultProps} canRelease={true} canStage={false} />)

    await openDropdown()

    const availableOption = screen.getByTestId('statusOption-available')
    expect(availableOption).toHaveAttribute('data-disabled')
  })

  it('shows the status badge and no dropdown trigger when isReadOnly is true', () => {
    render(<StatusDropdown {...defaultProps} isReadOnly={true} />)

    expect(screen.getByTestId('statusBadge')).toHaveTextContent('status.DRAFT')
    expect(screen.queryByTestId('statusDropdown')).toBeNull()
  })

  it('gives the status badge the height of the dropdown trigger when isReadOnly is true', () => {
    render(<StatusDropdown {...defaultProps} isReadOnly={true} />)

    expect(screen.getByTestId('statusBadge')).toHaveClass('h-9')
    expect(screen.getByTestId('statusBadge')).not.toHaveClass('h-7')
  })

  it('keeps the status hint next to the status badge when isReadOnly is true', async () => {
    const { container } = render(<StatusDropdown {...defaultProps} isReadOnly={true} statusHint="Test hint" />)

    expect(screen.getByTestId('statusBadge')).toBeInTheDocument()
    await userEvent.hover(container.querySelector('.lucide-info') as Element)
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Test hint')
  })
})
