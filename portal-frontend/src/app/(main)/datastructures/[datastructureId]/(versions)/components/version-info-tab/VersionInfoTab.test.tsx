import { render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import type { DatastructureVersionFormData } from '@/types/datastructures'

import { VersionInfoTab } from './VersionInfoTab'

const THING_TREE = 'urn:core:platform:civitas:datastructure:frost:9cc6ed95-0f8b-8ace-ab6e-83cdee87b3dd'
const UNPUBLISHED = 'urn:core:platform:civitas:datastructure:frost:1d2e3f40-5a6b-8c7d-9e0f-1a2b3c4d5e6f'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/published-structures/clientRequests', () => ({
  useGetPublishedStructures: () => ({
    data: {
      data: [{ sink: 'FROST', structures: [{ key: 'thing-tree', name: 'ThingTree', urn: `${THING_TREE}:1.1.0` }] }],
    },
  }),
}))

const TabWithForm = (props: { importedStructureUrns: string[] }) => {
  const { importedStructureUrns } = props
  const form = useForm<DatastructureVersionFormData>({ defaultValues: { version: '1.0.0', description: '' } })
  return <VersionInfoTab form={form} isAvailable={false} importedStructureUrns={importedStructureUrns} />
}

describe('VersionInfoTab', () => {
  it('names a structure it was built from by its published name and the pinned version', () => {
    render(<TabWithForm importedStructureUrns={[`${THING_TREE}:1.0.0`]} />)

    expect(screen.getByTitle(`${THING_TREE}:1.0.0`)).toHaveTextContent('ThingTree 1.0.0')
  })

  it('shows the logical URN of a structure that is no longer published', () => {
    render(<TabWithForm importedStructureUrns={[`${UNPUBLISHED}:2.0.0`]} />)

    expect(screen.getByTitle(`${UNPUBLISHED}:2.0.0`)).toHaveTextContent(`${UNPUBLISHED} 2.0.0`)
  })
})
