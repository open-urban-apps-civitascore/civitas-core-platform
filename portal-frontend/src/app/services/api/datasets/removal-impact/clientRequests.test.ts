import { vi } from 'vitest'

import { apiRequest } from '../../request/apiRequest'
import { getRemovalImpact } from './clientRequests'

vi.mock('../../request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

describe('getRemovalImpact', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiRequest).mockResolvedValue({ data: {} } as never)
  })

  it('sends deleted Pipelines and removed links as repeated query params', async () => {
    await getRemovalImpact('dataset-1', {
      deletedPipelineIds: ['pipeline-1'],
      removedLinks: [
        { pipelineId: 'pipeline-2', dataSinkId: 'sink-1' },
        { pipelineId: 'pipeline-3', dataSinkId: 'sink-2' },
      ],
    })

    const request = vi.mocked(apiRequest).mock.calls[0][0]
    expect(request.endpoint).toBe('/datasets/dataset-1/removal-impact')
    expect(request.params?.getAll('deletedPipelineIds')).toEqual(['pipeline-1'])
    expect(request.params?.getAll('removedLinks')).toEqual(['pipeline-2:sink-1', 'pipeline-3:sink-2'])
  })
})
