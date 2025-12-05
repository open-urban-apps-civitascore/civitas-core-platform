import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { fn } from 'storybook/test'

import { ActionButtons } from './ActionButtons'
import description from './ActionButtons.md?raw'

const meta = {
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
  argTypes: {
    confirmButtonType: {
      control: 'radio',
      options: ['button', 'submit'],
      description: 'Use "submit" when inside a form, "button" for standalone click handlers',
      table: {
        defaultValue: { summary: 'button' },
      },
    },
    onCancelClick: {
      action: 'cancel clicked',
      description: 'Callback fired when cancel button is clicked',
    },
    onConfirmClick: {
      action: 'confirm clicked',
      description: 'Callback fired when confirm button is clicked (only for button type)',
      if: { arg: 'confirmButtonType', eq: 'button' }, // Conditional visibility
    },
    hasCard: {
      control: 'boolean',
      description: 'Whether to wrap buttons in a ContentCard',
      table: {
        defaultValue: { summary: 'true' },
      },
    },
    isCancelButtonDisabled: {
      control: 'boolean',
      description: 'Disables the cancel button',
    },
    isConfirmButtonDisabled: {
      control: 'boolean',
      description: 'Disables the confirm button',
    },
    confirmButtonTitle: {
      control: 'text',
      description: 'Custom text for confirm button (defaults to translated "Submit")',
    },
  },
} satisfies Meta<typeof ActionButtons>

export default meta

type Story = StoryObj<typeof meta>

export const DefaultWithCard: Story = {
  args: {},
}

export const CancelDisabled: Story = {
  args: {
    isCancelButtonDisabled: true,
  },
}

export const ConfirmDisabled: Story = {
  args: {
    isConfirmButtonDisabled: true,
  },
}

export const BothDisabled: Story = {
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

export const AsSubmit: Story = {
  args: {
    confirmButtonType: 'submit',
  },
}
