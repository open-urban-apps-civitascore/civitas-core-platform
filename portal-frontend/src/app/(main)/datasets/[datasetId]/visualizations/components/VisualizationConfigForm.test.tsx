import { zodResolver } from '@hookform/resolvers/zod'
import { render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import { VisualizationFormData, visualizationFormSchema } from '@/types/visualizations'

import { VisualizationConfigForm } from './VisualizationConfigForm'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

vi.mock('@/components/form/fields/TextField', () => ({
  TextField: ({ name, label }: { name: string; label: string }) => (
    <input data-testid={`field-${name}`} aria-label={label} />
  ),
}))

vi.mock('@/components/form/fields/FormTextArea', () => ({
  FormTextArea: ({ name, label }: { name: string; label: string }) => (
    <textarea data-testid={`field-${name}`} aria-label={label} />
  ),
}))

const Wrapper = ({ typeLabel = 'Dashboard' }: { typeLabel?: string }) => {
  const form = useForm<VisualizationFormData>({
    resolver: zodResolver(visualizationFormSchema),
    defaultValues: { name: '', description: '' },
  })
  return <VisualizationConfigForm form={form} typeLabel={typeLabel} />
}

describe('VisualizationConfigForm', () => {
  it('displays the visualization type label', () => {
    render(<Wrapper typeLabel="Dashboard" />)
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })

  it('renders the name field', () => {
    render(<Wrapper />)
    expect(screen.getByTestId('field-name')).toBeInTheDocument()
  })

  it('renders the description field', () => {
    render(<Wrapper />)
    expect(screen.getByTestId('field-description')).toBeInTheDocument()
  })
})
