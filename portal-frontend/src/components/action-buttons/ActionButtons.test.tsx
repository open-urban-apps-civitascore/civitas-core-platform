import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ActionButtons } from './ActionButtons'

const CANCEL_BUTTON = 'actions.cancel'
const CONFIRM_BUTTON = 'actions.submit'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const onCancelClickMock = vi.fn()
const onConfirmClickMock = vi.fn()
const handleSubmitMock = vi.fn()

describe('SearchField', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  it('renders the buttons', () => {
    render(
      <ActionButtons
        confirmButtonType="submit"
        confirmButtonTitle={CONFIRM_BUTTON}
        onCancelClick={onCancelClickMock}
      />,
    )
    const cancelButton = screen.getByRole('button', { name: CANCEL_BUTTON })
    const confirmButton = screen.getByRole('button', { name: CONFIRM_BUTTON })

    expect(cancelButton).toBeInTheDocument()
    expect(confirmButton).toBeInTheDocument()
    expect(cancelButton).toBeEnabled()
    expect(confirmButton).toBeEnabled()
  })

  it('triggers a form submit if the confirm button with type "submit" is clicked', () => {
    render(
      <form onSubmit={handleSubmitMock}>
        <ActionButtons
          confirmButtonType="submit"
          confirmButtonTitle={CONFIRM_BUTTON}
          onCancelClick={onCancelClickMock}
        />
      </form>,
    )
    fireEvent.click(screen.getByText(CONFIRM_BUTTON))
    expect(handleSubmitMock).toHaveBeenCalled()
  })

  it('triggers the onConfirmClick function if the confirm button with type is "button" is clicked', () => {
    render(
      <form onSubmit={handleSubmitMock}>
        <ActionButtons
          confirmButtonType="button"
          confirmButtonTitle={CONFIRM_BUTTON}
          onCancelClick={onCancelClickMock}
          onConfirmClick={onConfirmClickMock}
        />
      </form>,
    )
    fireEvent.click(screen.getByRole('button', { name: CONFIRM_BUTTON }))
    expect(handleSubmitMock).not.toHaveBeenCalled()
    expect(onConfirmClickMock).toHaveBeenCalled()
  })

  it('triggers the onCancelClick function if cancel button is clicked', () => {
    render(
      <form onSubmit={handleSubmitMock}>
        <ActionButtons
          confirmButtonType="button"
          confirmButtonTitle={CONFIRM_BUTTON}
          onCancelClick={onCancelClickMock}
          onConfirmClick={onConfirmClickMock}
        />
      </form>,
    )
    fireEvent.click(screen.getByRole('button', { name: CANCEL_BUTTON }))
    expect(handleSubmitMock).not.toHaveBeenCalled()
    expect(onConfirmClickMock).not.toHaveBeenCalled()
    expect(onCancelClickMock).toHaveBeenCalled()
  })

  it('disables the buttons if isCancelButtonDisabled and isConfirmButtonDisabled are true', () => {
    render(
      <form onSubmit={handleSubmitMock}>
        <ActionButtons
          confirmButtonType="button"
          confirmButtonTitle={CONFIRM_BUTTON}
          onCancelClick={onCancelClickMock}
          onConfirmClick={onConfirmClickMock}
          isCancelButtonDisabled
          isConfirmButtonDisabled
        />
      </form>,
    )
    const cancelButton = screen.getByRole('button', { name: CANCEL_BUTTON })
    const confirmButton = screen.getByRole('button', { name: CONFIRM_BUTTON })

    expect(cancelButton).toBeDisabled()
    expect(confirmButton).toBeDisabled()
    fireEvent.click(cancelButton)
    expect(onConfirmClickMock).not.toHaveBeenCalled()
    fireEvent.click(confirmButton)
    expect(onConfirmClickMock).not.toHaveBeenCalled()
  })
})
