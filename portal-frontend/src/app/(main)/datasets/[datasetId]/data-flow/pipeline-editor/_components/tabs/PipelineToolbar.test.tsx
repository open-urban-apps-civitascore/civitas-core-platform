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
    activeSessionId: 'session-1',
    deletePipeline: vi.fn(),
    renamePipeline: vi.fn(),
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

  it('keeps the menu for rename when delete permission is missing', async () => {
    mocks.permissions.canDeletePipeline = false

    render(<PipelineToolbar />)
    await openMenu()

    expect(screen.getByText('toolbar.renamePipeline')).toBeInTheDocument()
    expect(screen.queryByText('toolbar.deletePipeline')).not.toBeInTheDocument()
  })

  it('hides the menu for a saved pipeline without edit or delete permission', () => {
    mocks.permissions.canDeletePipeline = false
    mocks.permissions.canEditPipeline = false

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

describe('PipelineToolbar — rename', () => {
  it('shows the pipeline name in bold', () => {
    render(<PipelineToolbar />)

    expect(screen.getByText('Test Pipeline')).toHaveClass('font-semibold')
  })

  it('hides the rename item without edit permission', async () => {
    mocks.permissions.canEditPipeline = false

    render(<PipelineToolbar />)
    await openMenu()

    expect(screen.queryByText('toolbar.renamePipeline')).not.toBeInTheDocument()
  })

  it('replaces the title with an input seeded with the current name when Rename is selected', async () => {
    render(<PipelineToolbar />)
    await openMenu()
    await userEvent.click(screen.getByText('toolbar.renamePipeline'))

    expect(screen.getByRole('textbox')).toHaveValue('Test Pipeline')
  })

  it('commits the new name on Enter', async () => {
    render(<PipelineToolbar />)
    await openMenu()
    await userEvent.click(screen.getByText('toolbar.renamePipeline'))

    const input = screen.getByRole('textbox')
    await userEvent.clear(input)
    await userEvent.type(input, 'Renamed Pipeline{Enter}')

    expect(mocks.activePipeline.renamePipeline).toHaveBeenCalledWith('Renamed Pipeline')
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('commits the new name on blur (click outside)', async () => {
    render(<PipelineToolbar />)
    await openMenu()
    await userEvent.click(screen.getByText('toolbar.renamePipeline'))

    const input = screen.getByRole('textbox')
    await userEvent.clear(input)
    await userEvent.type(input, 'Renamed Pipeline')
    await userEvent.click(document.body)

    expect(mocks.activePipeline.renamePipeline).toHaveBeenCalledWith('Renamed Pipeline')
  })

  it('discards the edit on Escape without committing', async () => {
    render(<PipelineToolbar />)
    await openMenu()
    await userEvent.click(screen.getByText('toolbar.renamePipeline'))

    const input = screen.getByRole('textbox')
    await userEvent.type(input, ' extra{Escape}')

    expect(mocks.activePipeline.renamePipeline).not.toHaveBeenCalled()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByText('Test Pipeline')).toBeInTheDocument()
  })
})
