import { render, screen } from '@testing-library/react'

import { InspectorHeader } from './InspectorHeader'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

describe('InspectorHeader', () => {
  it('shows the node type below a custom label', () => {
    render(<InspectorHeader nodeType="mapping" label="Sensor to Observation" isConfigured />)

    expect(screen.getByText('Sensor to Observation')).toBeInTheDocument()
    expect(screen.getByText('nodeTypes.mapping')).toBeInTheDocument()
  })

  it('shows the node type only once when the label is the node type', () => {
    render(<InspectorHeader nodeType="mapping" label="nodeTypes.mapping" isConfigured />)

    expect(screen.getAllByText('nodeTypes.mapping')).toHaveLength(1)
  })
})
