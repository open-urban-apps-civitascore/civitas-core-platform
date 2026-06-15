import { fireEvent, render, screen } from '@testing-library/react'

import { ExitWarningModal } from './ExitWarningModal'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const onDiscardMock = vi.fn()
const onConfirmMock = vi.fn()

const defaultProps = {
  open: true,
  onOpenChange: vi.fn(),
  onDiscard: onDiscardMock,
  onConfirm: onConfirmMock,
}

describe('ExitWarningModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders when open is true', () => {
    render(<ExitWarningModal {...defaultProps} />)
    expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
  })

  it('is not rendered when open is false', () => {
    render(<ExitWarningModal {...defaultProps} open={false} />)
    expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
  })

  it('calls onDiscard when discard button is clicked', () => {
    render(<ExitWarningModal {...defaultProps} />)
    fireEvent.click(screen.getByTestId('discardButton'))
    expect(onDiscardMock).toHaveBeenCalled()
  })

  it('calls onConfirm when save button is clicked', () => {
    render(<ExitWarningModal {...defaultProps} />)
    fireEvent.click(screen.getByTestId('saveButton'))
    expect(onConfirmMock).toHaveBeenCalled()
  })

  it('disables both buttons when isLoading is true', () => {
    render(<ExitWarningModal {...defaultProps} isLoading />)
    expect(screen.getByTestId('discardButton')).toBeDisabled()
    expect(screen.getByTestId('saveButton')).toBeDisabled()
  })

  it('shows custom title and description when provided', () => {
    render(<ExitWarningModal {...defaultProps} title="Custom Title" description="Custom description" />)
    expect(screen.getByText('Custom Title')).toBeInTheDocument()
    expect(screen.getByText('Custom description')).toBeInTheDocument()
  })

  it('shows default title and description when no custom title and description provided', () => {
    render(<ExitWarningModal {...defaultProps} />)
    expect(screen.getByText('title')).toBeInTheDocument()
    expect(screen.getByText('description')).toBeInTheDocument()
  })
})
