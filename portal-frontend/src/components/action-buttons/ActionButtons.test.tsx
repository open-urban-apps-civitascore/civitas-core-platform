import { fireEvent, render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/en.json'

import { ActionButtons } from './ActionButtons'

const CANCEL_BUTTON = 'Cancel'
const CONFIRM_BUTTON = 'Confirm'

const onCancelClickMock = vi.fn()
const onConfirmClickMock = vi.fn()
const handleSubmitMock = vi.fn()

describe('SearchField', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  it('renders the buttons', () => {
    render(
      <NextIntlClientProvider locale="en" messages={messages}>
        <ActionButtons
          confirmButtonType="submit"
          confirmButtonTitle={CONFIRM_BUTTON}
          onCancelClick={onCancelClickMock}
        />
      </NextIntlClientProvider>,
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
      <NextIntlClientProvider locale="en" messages={messages}>
        <form onSubmit={handleSubmitMock}>
          <ActionButtons
            confirmButtonType="submit"
            confirmButtonTitle={CONFIRM_BUTTON}
            onCancelClick={onCancelClickMock}
          />
        </form>
      </NextIntlClientProvider>,
    )
    fireEvent.click(screen.getByText(CONFIRM_BUTTON))
    expect(handleSubmitMock).toHaveBeenCalled()
  })

  it('triggers the onConfirmClick function if the confirm button with type is "button" is clicked', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <form onSubmit={handleSubmitMock}>
          <ActionButtons
            confirmButtonType="button"
            confirmButtonTitle={CONFIRM_BUTTON}
            onCancelClick={onCancelClickMock}
            onConfirmClick={onConfirmClickMock}
          />
        </form>
      </NextIntlClientProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: CONFIRM_BUTTON }))
    expect(handleSubmitMock).not.toHaveBeenCalled()
    expect(onConfirmClickMock).toHaveBeenCalled()
  })

  it('triggers the onCancelClick function if cancel button is clicked', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <form onSubmit={handleSubmitMock}>
          <ActionButtons
            confirmButtonType="button"
            confirmButtonTitle={CONFIRM_BUTTON}
            onCancelClick={onCancelClickMock}
            onConfirmClick={onConfirmClickMock}
          />
        </form>
      </NextIntlClientProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: CANCEL_BUTTON }))
    expect(handleSubmitMock).not.toHaveBeenCalled()
    expect(onConfirmClickMock).not.toHaveBeenCalled()
    expect(onCancelClickMock).toHaveBeenCalled()
  })

  it('disables the buttons if isCancelButtonDisabled and isConfirmButtonDisabled are true', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <form onSubmit={handleSubmitMock}>
          <ActionButtons
            confirmButtonType="button"
            confirmButtonTitle={CONFIRM_BUTTON}
            onCancelClick={onCancelClickMock}
            onConfirmClick={onConfirmClickMock}
            isCancelButtonDisabled
            isConfirmButtonDisabled
          />
        </form>
      </NextIntlClientProvider>,
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
