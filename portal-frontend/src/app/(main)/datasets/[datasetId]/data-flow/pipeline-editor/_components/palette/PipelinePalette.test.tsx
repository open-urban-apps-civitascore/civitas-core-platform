import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { ReadOnlyProvider } from '../../_hooks/use-pipeline-read-only'
import { PipelinePalette } from './PipelinePalette'

const visibleItemsByCategory = () =>
  Object.fromEntries(
    screen
      .getAllByRole('button')
      .map(header => [
        header.textContent,
        [...(header.parentElement?.querySelectorAll('[draggable]') ?? [])].map(
          item => item.lastElementChild?.firstElementChild?.textContent,
        ),
      ]),
  )

describe('PipelinePalette', () => {
  it('offers exactly start, end, cron, dataSource, mapping, frost and geoPersistence, grouped by category', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <ReadOnlyProvider isReadOnly={false}>
          <PipelinePalette />
        </ReadOnlyProvider>
      </NextIntlClientProvider>,
    )

    expect(visibleItemsByCategory()).toEqual({
      Allgemein: ['Pipeline Start', 'Pipeline Ende'],
      Trigger: ['Geplanter Trigger'],
      Quellen: ['Datenquelle'],
      Transformation: ['Mapping'],
      Speicher: ['Sensordaten-Speicher', 'Geodaten-Speicher'],
    })
  })
})
