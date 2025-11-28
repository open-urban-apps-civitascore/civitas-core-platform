import { fireEvent, render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/en.json'
import { ActionButtons } from './ActionButtons'

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
        <ActionButtons confirmButtonType="submit" confirmButtonTitle="Test Button" onCancelClick={onCancelClickMock} />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Test Button' })).toBeInTheDocument()
  })
  
  it('triggers a form submit if confirmbutton type is "submit"', () => {
    render(
      <NextIntlClientProvider locale="en" messages={messages}>
        <form onSubmit={handleSubmitMock}>
          <ActionButtons
            confirmButtonType="submit"
            confirmButtonTitle="Test Button"
            onCancelClick={onCancelClickMock}
          />
        </form>
      </NextIntlClientProvider>,
    )
    fireEvent.click(screen.getByText('Test Button'))
    expect(handleSubmitMock).toHaveBeenCalled()
  })

  it('triggers the onclick function if confirm button type is "button"', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <form onSubmit={handleSubmitMock}>
          <ActionButtons
            isCancelButtonDisabled
            confirmButtonType="button"
            confirmButtonTitle="Test Button"
            onCancelClick={() => {}}
            onConfirmClick={onConfirmClickMock}
          />
        </form>
      </NextIntlClientProvider>,
    )
    fireEvent.click(screen.getByText('Test Button'))
    expect(handleSubmitMock).not.toHaveBeenCalled()
    expect(onConfirmClickMock).toHaveBeenCalled()
  })
})
