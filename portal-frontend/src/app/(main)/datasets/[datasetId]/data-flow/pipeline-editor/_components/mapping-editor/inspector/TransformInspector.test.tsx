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

  it('renders the value field as a plain text input', () => {
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
})
