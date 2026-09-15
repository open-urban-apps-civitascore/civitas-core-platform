import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ReadOnlyProvider } from '../../hooks/use-read-only'
import type { DiagramSession } from '../../types/session'
import { TabBar } from './TabBar'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const createMockSession = (overrides?: Partial<DiagramSession>): DiagramSession => ({
  id: 'session-1',
  name: 'Untitled Diagram',
  diagram: {
    id: 'diagram-1',
    name: 'Untitled Diagram',
    nodes: [],
    edges: [],
    lastModified: new Date(),
    isDirty: false,
  },
  isDirty: false,
  dirtyFields: new Set(),
  lastModified: new Date(),
  created: new Date(),
  ...overrides,
})

const renderTabBar = (props?: Partial<React.ComponentProps<typeof TabBar>>, isReadOnly = false) => {
  const defaultProps: React.ComponentProps<typeof TabBar> = {
    sessions: [createMockSession()],
    activeSessionId: 'session-1',
    isMultiSessionMode: true,
    onSelectSession: vi.fn(),
    onCloseSession: vi.fn(),
    onRenameSession: vi.fn(),
    onCreateSession: vi.fn(),
    onImportClick: vi.fn(),
    onExportClick: vi.fn(),
    ...props,
  }

  return {
    ...render(
      <ReadOnlyProvider isReadOnly={isReadOnly}>
        <TabBar {...defaultProps} />
      </ReadOnlyProvider>,
    ),
    props: defaultProps,
  }
}

describe('TabBar', () => {
  it('displays modelNamePlaceholder when session name is "Untitled Diagram"', () => {
    renderTabBar({ sessions: [createMockSession({ name: 'Untitled Diagram' })] })

    expect(screen.getByText('modelNamePlaceholder')).toBeInTheDocument()
    expect(screen.queryByText('Untitled Diagram')).not.toBeInTheDocument()
  })

  it('displays modelNamePlaceholder when session name is empty', () => {
    renderTabBar({ sessions: [createMockSession({ name: '' })] })

    expect(screen.getByText('modelNamePlaceholder')).toBeInTheDocument()
  })

  it('displays the custom session name when provided', () => {
    renderTabBar({ sessions: [createMockSession({ name: 'SchoolModel' })] })

    expect(screen.getByText('SchoolModel')).toBeInTheDocument()
    expect(screen.queryByText('modelNamePlaceholder')).not.toBeInTheDocument()
  })

  it('displays dirty indicator bullet when session is dirty', () => {
    renderTabBar({ sessions: [createMockSession({ name: 'SchoolModel', isDirty: true })] })

    expect(screen.getByText('•')).toBeInTheDocument()
  })

  it('calls onSelectSession when clicking a tab', () => {
    const { props } = renderTabBar()

    fireEvent.click(screen.getByText('modelNamePlaceholder'))
    expect(props.onSelectSession).toHaveBeenCalledWith('session-1')
  })

  it('switches to input on double-click and initializes with empty string when name is "Untitled Diagram"', () => {
    renderTabBar({ sessions: [createMockSession({ name: 'Untitled Diagram' })] })

    const tabLabel = screen.getByText('modelNamePlaceholder')
    fireEvent.doubleClick(tabLabel)

    const input = screen.getByPlaceholderText('modelNamePlaceholder')
    expect(input).toBeInTheDocument()
    expect(input).toHaveValue('')
  })

  it('switches to input on double-click and initializes with custom name', () => {
    renderTabBar({ sessions: [createMockSession({ name: 'CustomName' })] })

    const tabLabel = screen.getByText('CustomName')
    fireEvent.doubleClick(tabLabel)

    const input = screen.getByPlaceholderText('modelNamePlaceholder')
    expect(input).toBeInTheDocument()
    expect(input).toHaveValue('CustomName')
  })

  it('renames session on Enter key press in edit mode', () => {
    const { props } = renderTabBar({ sessions: [createMockSession({ name: 'OldName' })] })

    fireEvent.doubleClick(screen.getByText('OldName'))
    const input = screen.getByPlaceholderText('modelNamePlaceholder')

    fireEvent.change(input, { target: { value: 'NewName' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(props.onRenameSession).toHaveBeenCalledWith('session-1', 'NewName')
  })

  it('cancels edit on Escape key press in edit mode without renaming', () => {
    const { props } = renderTabBar({ sessions: [createMockSession({ name: 'OldName' })] })

    fireEvent.doubleClick(screen.getByText('OldName'))
    const input = screen.getByPlaceholderText('modelNamePlaceholder')

    fireEvent.change(input, { target: { value: 'NewName' } })
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(props.onRenameSession).not.toHaveBeenCalled()
    expect(screen.getByText('OldName')).toBeInTheDocument()
  })

  it('keeps existing name and does not call onRenameSession when submitting empty input with Enter', () => {
    const { props } = renderTabBar({ sessions: [createMockSession({ name: 'OldName' })] })

    fireEvent.doubleClick(screen.getByText('OldName'))
    const input = screen.getByPlaceholderText('modelNamePlaceholder')

    fireEvent.change(input, { target: { value: '   ' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(props.onRenameSession).not.toHaveBeenCalled()
    expect(screen.getByText('OldName')).toBeInTheDocument()
  })

  it('keeps existing name and does not call onRenameSession on blur with empty input', () => {
    const { props } = renderTabBar({ sessions: [createMockSession({ name: 'OldName' })] })

    fireEvent.doubleClick(screen.getByText('OldName'))
    const input = screen.getByPlaceholderText('modelNamePlaceholder')

    fireEvent.change(input, { target: { value: '' } })
    fireEvent.blur(input)

    expect(props.onRenameSession).not.toHaveBeenCalled()
    expect(screen.getByText('OldName')).toBeInTheDocument()
  })

  it('renders Import and Export buttons when callbacks are provided', () => {
    renderTabBar()

    expect(screen.getByText('import.title')).toBeInTheDocument()
    expect(screen.getByText('export.title')).toBeInTheDocument()
  })

  it('calls onImportClick when clicking the Import button', () => {
    const { props } = renderTabBar()

    fireEvent.click(screen.getByText('import.title'))
    expect(props.onImportClick).toHaveBeenCalledTimes(1)
  })

  it('calls onExportClick when clicking the Export button', () => {
    const { props } = renderTabBar()

    fireEvent.click(screen.getByText('export.title'))
    expect(props.onExportClick).toHaveBeenCalledTimes(1)
  })

  it('hides the Import button in read-only mode while keeping the Export button visible', () => {
    renderTabBar({}, true)

    expect(screen.queryByText('import.title')).not.toBeInTheDocument()
    expect(screen.getByText('export.title')).toBeInTheDocument()
  })

  it('does not render session tabs when isMultiSessionMode is false', () => {
    renderTabBar({ isMultiSessionMode: false, sessions: [createMockSession({ name: 'SchoolModel' })] })

    expect(screen.queryByText('SchoolModel')).not.toBeInTheDocument()
    expect(screen.getByText('import.title')).toBeInTheDocument()
    expect(screen.getByText('export.title')).toBeInTheDocument()
  })
})
