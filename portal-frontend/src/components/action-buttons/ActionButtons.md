A standardized button group component that provides consistent Cancel and Confirm actions across the application.

## Overview

The ActionButtons component displays two buttons (Cancel and Confirm) that can be used in forms, dialogs, or any workflow requiring user confirmation. The buttons are styled consistently and can optionally be wrapped in a ContentCard for visual grouping.

## Key Features

- **Dual-mode operation**: Supports both standalone button handlers and form submission
- **Flexible styling**: Can be rendered with or without a card wrapper
- **Customizable**: Allows custom titles and individual button disable states
- **Accessible**: Uses semantic button types and proper ARIA attributes
- **Internationalized**: Button labels are automatically translated

## When to Use

Use this component when you need:

- A consistent Cancel/Confirm pattern in forms or dialogs
- To submit form data (use `confirmButtonType: 'submit'`)
- To trigger custom actions with confirmation (use `confirmButtonType: 'button'`)
- To prevent user actions temporarily (using disabled states)

## Button Modes

### Submit Mode (`confirmButtonType: 'submit'`)

Use this mode when the buttons are part of a form. The Confirm button will trigger form submission, while Cancel acts as a reset button.

**Best for**: Forms, data entry screens, settings pages

### Button Mode (`confirmButtonType: 'button'`)

Use this mode for standalone actions that aren't tied to form submission. Both buttons trigger their respective click handlers.

**Best for**: Dialogs, confirmation modals, multi-step wizards
