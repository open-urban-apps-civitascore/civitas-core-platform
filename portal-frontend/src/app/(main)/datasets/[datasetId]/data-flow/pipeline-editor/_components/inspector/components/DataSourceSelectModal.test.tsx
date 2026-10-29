import { render, screen, within } from '@testing-library/react'

import { mockDatasources } from '@/__mocks__/datasources/datasources.mock'

import { DataSourceSelectModal } from './DataSourceSelectModal'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

describe('DataSourceSelectModal', () => {
  it('shows the status of each data source as a status badge', () => {
    render(
      <DataSourceSelectModal
        isOpen
        onOpenChange={vi.fn()}
        datasources={mockDatasources}
        isLoading={false}
        selectedId={undefined}
        onSelect={vi.fn()}
      />,
    )

    const rowOf = (name: string) => screen.getByRole('checkbox', { name }).closest('tr') as HTMLElement
    expect(within(rowOf('MQTT Sensor Data')).getByTestId('statusBadge')).toHaveTextContent(/^status.DRAFT$/)
    expect(within(rowOf('SQL Database Import')).getByTestId('statusBadge')).toHaveTextContent(/^status.AVAILABLE$/)
  })
})
