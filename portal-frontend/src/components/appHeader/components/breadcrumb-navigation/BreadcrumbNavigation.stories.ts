import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { BreadcrumbNavigation } from './BreadcrumbNavigation'
import description from './BreadcrumbNavigation.md?raw'

const meta = {
  title: 'Components/BreadcrumbNavigation',
  component: BreadcrumbNavigation,
  parameters: {
    docs: {
      description: {
        component: description,
      },
    },
    nextjs: {
      navigation: {
        pathname: '/menu-item/sub-item/documentation',
      },
    },
  },
  args: {},
} satisfies Meta<typeof BreadcrumbNavigation>

export default meta

type Story = StoryObj<typeof meta>

export const Default: Story = {
  args: {},
}
