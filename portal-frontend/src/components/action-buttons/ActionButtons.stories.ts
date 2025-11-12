import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { fn } from 'storybook/test'

import { ActionButtons } from './ActionButtons'
import description from './ActionButtons.md?raw'

const meta: Meta = {
  title: 'Components/ActionButtons',
  component: ActionButtons,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: description,
      },
    },
  },
  args: {
    confirmButtonType: 'button',
    onCancelClick: fn(),
    onConfirmClick: fn(),
    hasCard: true,
    isCancelButtonDisabled: false,
    isConfirmButtonDisabled: false,
  },
} satisfies Meta<typeof ActionButtons>

export default meta

type Story = StoryObj<typeof meta>

export const Default: Story = {
  args: {},
}

export const Disabled: Story = {
  args: {
    isCancelButtonDisabled: true,
    isConfirmButtonDisabled: true,
  },
}

export const WithoutCard: Story = {
  args: {
    hasCard: false,
  },
}

export const WithCustomTitle: Story = {
  args: {
    confirmButtonType: 'button',
    confirmButtonTitle: 'Confirm',
  },
}
