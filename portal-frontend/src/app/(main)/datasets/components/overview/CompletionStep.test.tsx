import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { CompletionStep } from './CompletionStep'

describe('CompletionStep', () => {
  it('renders the CompletionStep with checked circle, one button and content', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <CompletionStep
          datasetId="1"
          step={{
            title: 'Test Title',
            buttons: [{ text: 'Test Button', routeParam: 'metadata' }],
            isCompleted: true,
            content: <p>Test</p>,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByTestId('completionStep')).toBeDefined()
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getByRole('link')).toHaveTextContent('Test Button')
    expect(screen.getByTestId('circleCheck')).toBeInTheDocument()
    expect(screen.queryByTestId('circle')).not.toBeInTheDocument()
    expect(screen.queryByTestId('completionStepContent')).toHaveTextContent('Test')
  })
  it('renders the CompletionStep with unchecked circle and two buttons', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <CompletionStep
          datasetId="1"
          step={{
            title: 'Test Title',
            buttons: [
              { text: 'Test Button 1', routeParam: 'metadata' },
              { text: 'Test Button 2', routeParam: 'data' },
            ],
            isCompleted: false,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByTestId('completionStep')).toBeDefined()
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    const buttons = screen.getAllByRole('link')
    expect(buttons).toHaveLength(2)
    expect(buttons[0]).toHaveTextContent('Test Button 1')
    expect(buttons[1]).toHaveTextContent('Test Button 2')
    expect(screen.getByTestId('circle')).toBeInTheDocument()
    expect(screen.queryByTestId('circleCheck')).not.toBeInTheDocument()
    expect(screen.queryByTestId('completionStepContent')).not.toBeInTheDocument()
  })

  it('renders disabled button correctly', async () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <CompletionStep
          datasetId="1"
          step={{
            title: 'Test Title',
            buttons: [{ text: 'Test Button', routeParam: 'metadata' }],
            isCompleted: false,
          }}
          disabled={true}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).toHaveTextContent('Test Button')
  })
})
