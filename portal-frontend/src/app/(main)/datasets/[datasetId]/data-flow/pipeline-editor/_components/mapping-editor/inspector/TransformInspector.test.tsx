import { render, screen } from '@testing-library/react'

import { mappingRegistry } from '../transforms'
import { TransformInspector } from './TransformInspector'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

describe('TransformInspector', () => {
  it('renders the type-specific placeholder in the value field', () => {
    render(
      <TransformInspector
        def={mappingRegistry.byType.const}
        config={{ type: 'Integer', value: '' }}
        onChange={vi.fn()}
      />,
    )

    expect(screen.getByPlaceholderText('42')).toBeInTheDocument()
  })

  it('keeps the value field a plain text input when the type has no textarea override', () => {
    render(
      <TransformInspector
        def={mappingRegistry.byType.const}
        config={{ type: 'String', value: '' }}
        onChange={vi.fn()}
      />,
    )

    const valueField = screen.getByDisplayValue('')
    expect(valueField.tagName).toBe('INPUT')
    expect(valueField).toHaveAttribute('type', 'text')
  })

  it('switches the value field to a textarea when the type overrides the control', () => {
    render(
      <TransformInspector def={mappingRegistry.byType.const} config={{ type: 'Json', value: '' }} onChange={vi.fn()} />,
    )

    expect(screen.getByDisplayValue('').tagName).toBe('TEXTAREA')
  })
})
