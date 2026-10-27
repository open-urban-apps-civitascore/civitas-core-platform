import { render, screen } from '@testing-library/react'

import { EntityMetadata } from './EntityMetadata'

describe('EntityMetadata', () => {
  it('renders a text value', () => {
    render(<EntityMetadata items={[{ label: 'Connector', value: 'MQTT' }]} />)
    expect(screen.getByText('MQTT')).toBeInTheDocument()
  })

  it('renders an element value', () => {
    render(<EntityMetadata items={[{ label: 'Status', value: <span data-testid="elementValue">Draft</span> }]} />)
    expect(screen.getByTestId('elementValue')).toHaveTextContent('Draft')
  })

  it('adds space above an element value only', () => {
    render(
      <EntityMetadata
        items={[
          { label: 'Connector', value: 'MQTT' },
          { label: 'Status', value: <span data-testid="elementValue">Draft</span> },
        ]}
      />,
    )
    expect(screen.getByTestId('elementValue').closest('dd')).toHaveClass('pt-1')
    expect(screen.getByText('MQTT').closest('dd')).not.toHaveClass('pt-1')
  })

  it('renders nothing when all values are empty', () => {
    const { container } = render(<EntityMetadata items={[{ label: 'Status', value: undefined }]} />)
    expect(container).toBeEmptyDOMElement()
  })
})
