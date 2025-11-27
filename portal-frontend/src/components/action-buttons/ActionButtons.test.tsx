import { fireEvent, getByRole, render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/en.json'
import { ActionButtons } from './ActionButtons'

const onCancelClickMock = vi.fn()
const handleSubmit = vi.fn()

describe('SearchField', () => {
  it('resnders the buttons', () => {
    render(
      <NextIntlClientProvider locale="en" messages={messages}>
        <ActionButtons confirmButtonType="submit" onCancelClick={onCancelClickMock} confirmButtonTitle="Test Button" />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Test Button' })).toBeInTheDocument()
  })
  it('triggers a form submit if confirmbutton type is "submit"', () => {
    render(
      <NextIntlClientProvider locale="en" messages={messages}>
        <form onSubmit={handleSubmit}>
          <ActionButtons
            confirmButtonType="submit"
            onCancelClick={onCancelClickMock}
            confirmButtonTitle="Test Button"
          />
        </form>
      </NextIntlClientProvider>,
    )
    fireEvent.click(screen.getByText('Test Button'))
    expect(handleSubmit).toHaveBeenCalled()
  })

  it('triggers a form submit if confirmbutton type is "submit"', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <form onSubmit={handleSubmit}>
          <ActionButtons
            confirmButtonType="submit"
            onCancelClick={onCancelClickMock}
            confirmButtonTitle="Test Button"
          />
        </form>
      </NextIntlClientProvider>,
    )
    fireEvent.click(screen.getByText('Test Button'))
    expect(handleSubmit).toHaveBeenCalled(1)
  })
})
