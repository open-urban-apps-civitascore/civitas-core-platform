import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ReadOnlyProvider } from '../../hooks/use-read-only'
import { ImportMenu } from './ImportMenu'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@xyflow/react', () => ({
  useReactFlow: () => ({ fitView: vi.fn() }),
}))

vi.mock('../../hooks/use-active-diagram', () => ({
  useActiveDiagram: () => ({
    diagram: { id: 'd1', name: 'D', nodes: [], edges: [], lastModified: new Date(), isDirty: false },
    dispatch: vi.fn(),
  }),
}))

vi.mock('@/app/services/api/published-structures/clientRequests', () => ({
  useGetPublishedStructures: () => ({
    isLoading: false,
    data: {
      data: [
        {
          sink: 'FROST',
          structures: [
            {
              key: 'thing-tree',
              name: 'ThingTree',
              urn: 'urn:core:platform:civitas:datastructure:frost:ThingTree:0123456789:1.0.0',
            },
            { key: 'things', name: 'Things' },
          ],
        },
      ],
    },
  }),
  useGetPublishedStructure: () => ({ data: undefined, isPlaceholderData: false, isError: false }),
}))

const renderMenu = (onImportFile?: () => void, isReadOnly = false) =>
  render(
    <ReadOnlyProvider isReadOnly={isReadOnly}>
      <ImportMenu onImportFile={onImportFile} />
    </ReadOnlyProvider>,
  )

describe('ImportMenu', () => {
  it('offers the file and the standard structures in one menu', async () => {
    const user = userEvent.setup()
    const onImportFile = vi.fn()
    renderMenu(onImportFile)

    await user.click(screen.getByText('import.title'))
    await user.click(await screen.findByText('import.fromFile'))

    expect(onImportFile).toHaveBeenCalledTimes(1)
  })

  it('lists the structures of a sink under the standard submenu, a structure without a version disabled', async () => {
    const user = userEvent.setup()
    renderMenu(vi.fn())

    await user.click(screen.getByText('import.title'))
    // Keyboard, not the pointer: jsdom has no layout, and a pointer that leaves one submenu for the
    // next closes the first before the second opens.
    await user.click(await screen.findByText('import.standard'))
    ;(await screen.findByText('FROST')).focus()
    await user.keyboard('{ArrowRight}')

    expect(await screen.findByText('ThingTree')).toBeInTheDocument()
    // Not published yet: there is no version to pin.
    expect(screen.getByText('Things').closest('[role="menuitem"]')).toHaveAttribute('aria-disabled', 'true')
  })

  it('is not offered on a released version', () => {
    renderMenu(vi.fn(), true)

    expect(screen.queryByText('import.title')).not.toBeInTheDocument()
  })
})
