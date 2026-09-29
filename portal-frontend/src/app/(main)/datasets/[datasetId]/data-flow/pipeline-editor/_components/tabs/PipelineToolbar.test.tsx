import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { PipelineToolbar } from './PipelineToolbar'

const mocks = vi.hoisted(() => ({
  permissions: { canDeletePipeline: true, canEditPipeline: true },
  activePipeline: {
    pipeline: { name: 'Test Pipeline' } as { id?: string; name: string } | undefined,
    isDirty: false,
    isDeleting: false,
    deletePipeline: vi.fn(),
    runValidation: vi.fn(),
  },
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useParams: () => ({ datasetId: 'dataset-1' }),
}))

vi.mock('@/hooks/use-dataset-permissions', () => ({
  useDatasetPermissionsById: () => mocks.permissions,
}))

vi.mock('../../_hooks/use-active-pipeline', () => ({
  useActivePipeline: () => mocks.activePipeline,
}))

const savedPipeline = { id: 'pipeline-1', name: 'Test Pipeline' }
const neverSavedPipeline = { name: 'Test Pipeline' }

const openMenu = () => userEvent.click(screen.getByTitle('toolbar.pipelineSettings'))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.permissions.canDeletePipeline = true
  mocks.permissions.canEditPipeline = true
  mocks.activePipeline.pipeline = savedPipeline
  mocks.activePipeline.isDeleting = false
})

describe('PipelineToolbar — delete and discard', () => {
  it('offers deleting a saved pipeline', async () => {
    render(<PipelineToolbar />)
    await openMenu()

    expect(screen.getByText('toolbar.deletePipeline')).toBeInTheDocument()
  })

  it('offers discarding a never-saved pipeline', async () => {
    mocks.activePipeline.pipeline = neverSavedPipeline

    render(<PipelineToolbar />)
    await openMenu()

    expect(screen.getByText('toolbar.discardPipeline')).toBeInTheDocument()
    expect(screen.queryByText('toolbar.deletePipeline')).not.toBeInTheDocument()
  })

  it('offers discarding a never-saved pipeline even without delete permission', async () => {
    mocks.permissions.canDeletePipeline = false
    mocks.activePipeline.pipeline = neverSavedPipeline

    render(<PipelineToolbar />)
    await openMenu()

    expect(screen.getByText('toolbar.discardPipeline')).toBeInTheDocument()
  })

  it('hides the menu for a saved pipeline without delete permission', () => {
    mocks.permissions.canDeletePipeline = false

    render(<PipelineToolbar />)

    expect(screen.queryByTitle('toolbar.pipelineSettings')).not.toBeInTheDocument()
  })

  it('words the confirmation as a delete for a saved pipeline', async () => {
    render(<PipelineToolbar />)
    await openMenu()
    await userEvent.click(screen.getByText('toolbar.deletePipeline'))

    expect(screen.getByText('toolbar.deleteConfirmTitle')).toBeInTheDocument()
    expect(screen.getByText('toolbar.deleteConfirmDescription')).toBeInTheDocument()
    expect(screen.getByTestId('confirmButton')).toHaveTextContent('toolbar.deletePipeline')
  })

  it('words the confirmation as a discard for a never-saved pipeline', async () => {
    mocks.activePipeline.pipeline = neverSavedPipeline

    render(<PipelineToolbar />)
    await openMenu()
    await userEvent.click(screen.getByText('toolbar.discardPipeline'))

    expect(screen.getByText('toolbar.discardConfirmTitle')).toBeInTheDocument()
    expect(screen.getByText('toolbar.discardConfirmDescription')).toBeInTheDocument()
    expect(screen.getByTestId('confirmButton')).toHaveTextContent('toolbar.discardPipeline')
  })

  it('discards the pipeline when the confirmation is accepted', async () => {
    mocks.activePipeline.pipeline = neverSavedPipeline

    render(<PipelineToolbar />)
    await openMenu()
    await userEvent.click(screen.getByText('toolbar.discardPipeline'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    expect(mocks.activePipeline.deletePipeline).toHaveBeenCalledOnce()
  })
})
