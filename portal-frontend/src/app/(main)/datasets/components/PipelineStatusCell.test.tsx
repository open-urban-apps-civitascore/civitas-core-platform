import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'
import { DATASET_STATUS_TYPES, DatasetStatusTypes, PipelineBasicInfo, PipelineRuntimeStatus } from '@/types/datasets'

import { PipelineStatusCell } from './PipelineStatusCell'

const pipeline = (state?: PipelineRuntimeStatus['state'], id = 'p'): PipelineBasicInfo => ({
  id,
  name: id,
  runtimeStatus: state ? { state } : undefined,
})

const renderCell = (status: DatasetStatusTypes, pipelines: PipelineBasicInfo[]) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <PipelineStatusCell status={status} pipelines={pipelines} />
    </NextIntlClientProvider>,
  )

describe('PipelineStatusCell', () => {
  it('shows the pipeline count for a draft dataset, including zero', () => {
    renderCell(DATASET_STATUS_TYPES.DRAFT, [])
    expect(screen.getByText('0')).toBeInTheDocument()
  })

  it('shows the pipeline count for a draft dataset with pipelines', () => {
    renderCell(DATASET_STATUS_TYPES.DRAFT, [pipeline('OK', 'a'), pipeline('ERROR', 'b')])
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('shows the pipeline count for a ready dataset', () => {
    renderCell(DATASET_STATUS_TYPES.READY, [pipeline('OK')])
    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('shows a single active badge without a count for one running pipeline', () => {
    renderCell(DATASET_STATUS_TYPES.AVAILABLE, [pipeline('OK')])
    expect(screen.getByText('Aktiv')).toBeInTheDocument()
  })

  it('prefixes the count when the dataset has more than one pipeline', () => {
    renderCell(DATASET_STATUS_TYPES.AVAILABLE, [pipeline('OK', 'a'), pipeline('OK', 'b')])
    expect(screen.getByText('2 Aktiv')).toBeInTheDocument()
  })

  it('shows one badge per state for mixed pipelines', () => {
    renderCell(DATASET_STATUS_TYPES.AVAILABLE, [pipeline('OK', 'a'), pipeline('OK', 'b'), pipeline('ERROR', 'c')])
    expect(screen.getByText('2 Aktiv')).toHaveClass('text-green-800')
    expect(screen.getByText('1 Fehler')).toHaveClass('text-red-700')
  })

  it('shows a stopped badge for a stopped pipeline', () => {
    renderCell(DATASET_STATUS_TYPES.AVAILABLE, [pipeline('STOPPED')])
    expect(screen.getByText('Gestoppt')).toHaveClass('text-red-700')
  })

  it('shows a dash for an available dataset without pipelines', () => {
    renderCell(DATASET_STATUS_TYPES.AVAILABLE, [])
    expect(screen.getByText('-')).toBeInTheDocument()
  })

  it('does not count a pipeline without a runtime status', () => {
    renderCell(DATASET_STATUS_TYPES.AVAILABLE, [pipeline(undefined)])
    expect(screen.getByText('-')).toBeInTheDocument()
    expect(screen.queryByText('Aktiv')).not.toBeInTheDocument()
  })
})
