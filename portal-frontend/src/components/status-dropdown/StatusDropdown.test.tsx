import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  canSetAvailable: true,
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
    render(<StatusDropdown {...defaultProps} canRelease={true} canSetAvailable={true} />)

    await openDropdown()

    const availableOption = screen.getByTestId('statusOption-available')
    expect(availableOption).not.toHaveAttribute('data-disabled')
  })

  it('disables Available option when canRelease is false', async () => {
    render(<StatusDropdown {...defaultProps} canRelease={false} canSetAvailable={true} />)

    await openDropdown()

    const availableOption = screen.getByTestId('statusOption-available')
    expect(availableOption).toHaveAttribute('data-disabled')
  })

  it('disables Available option when canSetAvailable is false', async () => {
    render(<StatusDropdown {...defaultProps} canRelease={true} canSetAvailable={false} />)

    await openDropdown()

    const availableOption = screen.getByTestId('statusOption-available')
    expect(availableOption).toHaveAttribute('data-disabled')
  })

  it('disables the dropdown trigger when isReadOnly is true', () => {
    render(<StatusDropdown {...defaultProps} isReadOnly={true} />)

    const trigger = screen.getByTestId('statusDropdown')
    expect(trigger).toBeDisabled()
  })
})
