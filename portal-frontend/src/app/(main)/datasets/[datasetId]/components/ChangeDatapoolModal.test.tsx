import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { ChangeDatapoolModal } from './ChangeDatapoolModal'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

describe('ChangeDatapoolModal', () => {
  it('renders all content when open', () => {
    render(<ChangeDatapoolModal isOpen={true} onDiscard={vi.fn()} onConfirm={vi.fn()} />)

    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('title')
    expect(dialog).toHaveTextContent('description')
    expect(dialog).toHaveTextContent('consequences.permissions')
    expect(dialog).toHaveTextContent('consequences.access')
    expect(dialog).toHaveTextContent('consequences.otherUsers')
    expect(dialog).toHaveTextContent('warnings.inheritance')
    expect(dialog).toHaveTextContent('warnings.checkPermissions')
    expect(dialog).toHaveTextContent('confirmButton')
    expect(dialog).toHaveTextContent('cancel')
  })

  it('is not rendered when closed', () => {
    render(<ChangeDatapoolModal isOpen={false} onDiscard={vi.fn()} onConfirm={vi.fn()} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('calls onConfirm when the confirm button is clicked', () => {
    const onConfirm = vi.fn()
    render(<ChangeDatapoolModal isOpen={true} onDiscard={vi.fn()} onConfirm={onConfirm} />)

    fireEvent.click(screen.getByText('confirmButton'))

    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('calls onDiscard when the cancel button is clicked', () => {
    const onDiscard = vi.fn()
    render(<ChangeDatapoolModal isOpen={true} onDiscard={onDiscard} onConfirm={vi.fn()} />)

    fireEvent.click(screen.getByText('cancel'))

    expect(onDiscard).toHaveBeenCalledOnce()
  })
})
