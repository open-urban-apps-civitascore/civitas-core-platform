import { fireEvent, render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { SearchField } from './SearchField'

const onChangeSearchString = vi.fn()

describe('SearchField', () => {
  beforeEach(() => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <SearchField onChangeSearchString={onChangeSearchString} searchString="" />
      </NextIntlClientProvider>,
    )
  })
  it('renders the search field with correct placeholder', () => {
    expect(screen.getByPlaceholderText('Durchsuchen...')).toBeInTheDocument()
  })

  it('updates input value when typing', () => {
    const input = screen.getByPlaceholderText('Durchsuchen...') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'hello' } })

    expect(input.value).toBe('hello')
  })
})
