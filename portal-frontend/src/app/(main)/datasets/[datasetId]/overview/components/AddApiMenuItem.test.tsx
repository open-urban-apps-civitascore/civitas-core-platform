import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { DropdownMenu, DropdownMenuContent } from '@/components/ui/dropdown-menu'

import { AddApiMenuItem } from './AddApiMenuItem'

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({ hasUnsavedChanges: false, requestNavigation: vi.fn() }),
}))

const renderItem = (canCreate: boolean) =>
  render(
    <DropdownMenu open>
      <DropdownMenuContent>
        <AddApiMenuItem
          href="/datasets/dataset-1/apis?type=sensorthings"
          title="SensorThings"
          subtitle="Sensor data"
          canCreate={canCreate}
        />
      </DropdownMenuContent>
    </DropdownMenu>,
  )

describe('AddApiMenuItem', () => {
  it('renders an enabled link to the create page when the API can be created', () => {
    renderItem(true)

    expect(screen.getByRole('menuitem')).not.toHaveAttribute('data-disabled')
    expect(screen.getByRole('menuitem')).toHaveAttribute('href', '/datasets/dataset-1/apis?type=sensorthings')
    expect(screen.getByText('Sensor data')).toBeInTheDocument()
  })

  it('renders a disabled item without a link when the API cannot be created', () => {
    renderItem(false)

    expect(screen.getByRole('menuitem')).toHaveAttribute('data-disabled')
    expect(screen.getByText('SensorThings').closest('a')).toBeNull()
    expect(screen.getByText('Sensor data')).toBeInTheDocument()
  })
})
