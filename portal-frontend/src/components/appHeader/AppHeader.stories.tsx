import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { SidebarProvider } from '@/components/ui/sidebar'

import { AppHeader } from './AppHeader'
import description from './AppHeader.md?raw'

const meta = {
  title: 'Components/AppHeader',
  component: AppHeader,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: description,
      },
    },
  },
  args: {},
  decorators: [
    story => {
      const Story = story
      return (
        <SidebarProvider>
          <Story />
        </SidebarProvider>
      )
    },
  ],
} satisfies Meta<typeof AppHeader>

export default meta

type TStory = StoryObj<typeof meta>

export const Default: TStory = {
  args: {},
}
