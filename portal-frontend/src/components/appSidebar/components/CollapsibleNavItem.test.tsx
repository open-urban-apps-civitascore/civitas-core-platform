import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'

vi.mock('@/components/ui/collapsible', () => ({
  Collapsible: ({
    children,
    open,
    ...props
    // eslint-disable-next-line react/boolean-prop-naming
  }: React.HTMLAttributes<HTMLDivElement> & { open?: boolean; onOpenChange?: (open: boolean) => void }) => (
    <div data-state={open ? 'open' : 'closed'} {...props}>
      {children}
    </div>
  ),
  CollapsibleTrigger: ({ children, ...props }: React.HTMLAttributes<HTMLButtonElement>) => (
    <button data-slot="collapsible-trigger" {...props}>
      {children}
    </button>
  ),
  CollapsibleContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: false,
    requestNavigation: vi.fn(),
    requestBack: vi.fn(),
  }),
}))

import { CollapsibleNavItem } from './CollapsibleNavItem'

const testItem = {
  title: 'ourData',
  url: '',
  items: [
    { title: 'datasets', url: '/datasets' },
    { title: 'datasources', url: '/datasources' },
  ],
}

const tNav = (key: string) => key

const renderItem = (pathname: string) =>
  render(
    <SidebarProvider>
      <CollapsibleNavItem item={testItem} pathname={pathname} tNav={tNav} />
    </SidebarProvider>,
  )

describe('CollapsibleNavItem', () => {
  it('expands the group whose sub-item matches the current pathname exactly', () => {
    const { container } = renderItem('/datasets')

    const collapsible = container.querySelector('[data-state]')
    expect(collapsible).toHaveAttribute('data-state', 'open')
  })

  it('keeps the group expanded on a sub-route (e.g. /datasets/create)', () => {
    const { container } = renderItem('/datasets/create')

    const collapsible = container.querySelector('[data-state]')
    expect(collapsible).toHaveAttribute('data-state', 'open')
  })

  it('highlights the matching sub-item on a sub-route', () => {
    const { getByTestId } = renderItem('/datasets/create')

    const datasetsItem = getByTestId('sidebarMenuItem-datasets')
    expect(datasetsItem.querySelector('a')).toHaveAttribute('data-active', 'true')
  })

  it('collapses the group when the pathname does not match any sub-item', () => {
    const { container } = renderItem('/')

    const collapsible = container.querySelector('[data-state]')
    expect(collapsible).toHaveAttribute('data-state', 'closed')
  })
})
