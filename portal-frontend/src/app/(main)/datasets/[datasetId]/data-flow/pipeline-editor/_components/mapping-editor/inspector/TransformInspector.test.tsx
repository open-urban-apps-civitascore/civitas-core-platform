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

  it('shows the format for string input and the unit for epoch input', () => {
    const def = mappingRegistry.byType.toDateTime
    const { rerender } = render(
      <TransformInspector def={def} config={{ inputType: 'string', pattern: 'yyyy-MM-dd' }} onChange={vi.fn()} />,
    )
    expect(screen.getByDisplayValue('yyyy-MM-dd')).toBeInTheDocument()
    expect(screen.queryByText('transforms.toDateTime.fields.unit.label')).not.toBeInTheDocument()

    rerender(<TransformInspector def={def} config={{ inputType: 'epoch', unit: 'milliseconds' }} onChange={vi.fn()} />)
    expect(screen.getByText('transforms.toDateTime.fields.unit.label')).toBeInTheDocument()
    expect(screen.queryByText('transforms.toDateTime.fields.pattern.label')).not.toBeInTheDocument()
  })
})
