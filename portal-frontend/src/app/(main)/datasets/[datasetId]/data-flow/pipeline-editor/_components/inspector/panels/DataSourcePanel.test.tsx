import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { usePipelineDatasources } from '../../../_hooks/use-pipeline-datasources'
import type { DataSourceNodeData } from '../../../_types/nodes'
import { DataSourcePanel } from './DataSourcePanel'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: vi.fn(),
}))

vi.mock('../../../_hooks/use-pipeline-datasources', () => ({
  usePipelineDatasources: vi.fn(),
}))

const datasource = { id: 'ds-1', name: 'Traffic', description: 'North ring', connectorType: 'MQTT' as const }

const mockPermissions = (granted: boolean) => {
  vi.mocked(usePermissions).mockReturnValue({
    hasPermission: (permission: string) => granted && permission === PERMISSION_NAMES.DATASOURCE_READ,
  } as unknown as ReturnType<typeof usePermissions>)
}

const nodeData: DataSourceNodeData = {
  label: 'Data Source',
  configured: true,
  entityType: 'datasource',
  entityId: 'ds-1',
} as DataSourceNodeData

const renderPanel = () => render(<DataSourcePanel data={nodeData} onUpdate={vi.fn()} />)

beforeEach(() => {
  vi.mocked(usePipelineDatasources).mockReturnValue({
    entities: [datasource],
    isLoading: false,
    isError: false,
    getEntityById: () => datasource,
    getName: () => datasource.name,
  })
})

describe('DataSourcePanel', () => {
  it('links to the data source page when the user may read data sources', () => {
    mockPermissions(true)
    renderPanel()

    expect(screen.getByText('dataSourcePanel.showDataStructure')).toBeInTheDocument()
  })

  it('omits the data source link without DATASOURCE_READ', () => {
    // Picking a source needs no DATASOURCE_READ, but opening its page does — the link would 403.
    mockPermissions(false)
    renderPanel()

    expect(screen.queryByText('dataSourcePanel.showDataStructure')).not.toBeInTheDocument()
  })

  it('shows the connector and description of the selected source', () => {
    mockPermissions(true)
    renderPanel()

    expect(screen.getByText('MQTT')).toBeInTheDocument()
    expect(screen.getByText('North ring')).toBeInTheDocument()
  })
})
