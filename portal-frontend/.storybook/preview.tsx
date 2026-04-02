import '../src/app/globals.css'

import type { Decorator, Preview } from '@storybook/nextjs-vite'
import { NextIntlClientProvider } from 'next-intl'

import defaultMessages from '../src/messages/en.json'

export const decorators: Decorator[] = [
  story => {
    const Story = story
    return (
      <NextIntlClientProvider locale="en" messages={defaultMessages}>
        <Story />
      </NextIntlClientProvider>
    )
  },
]

const preview: Preview = {
  decorators,
  tags: ['autodocs'],
  parameters: {
    nextjs: {
      appDirectory: true,
    },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    options: {
      storySort: {
        order: ['Getting Started'],
      },
    },
    a11y: {
      // 'todo' - show a11y violations in the test UI only
      // 'error' - fail CI on a11y violations
      // 'off' - skip a11y checks entirely
      test: 'todo',
    },
  },
}

export default preview
