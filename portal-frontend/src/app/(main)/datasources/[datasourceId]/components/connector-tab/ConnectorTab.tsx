import { useTranslations } from 'next-intl'
import { Path, UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { ConnectorType } from '@/types/connectors'
import { ConnectorField as FormField, DatasourceFormDraft } from '@/types/datasources'

import { FooterElement } from '../FooterElement'
import { CONNECTORS } from './connectorSources'
import { DynamicFormField } from './DynamicFormField'

interface ConnectorTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  isDraftMode: boolean
  connectorType: ConnectorType | undefined
  formfields: FormField[]
}
export const ConnectorTab = (props: ConnectorTabProps) => {
  const { form, formfields: config, isDraftMode } = props
  const t = useTranslations('datasources.connectorTab')
  const tCommon = useTranslations('common')
  const connectorTypeOptions: SelectOption[] = Object.entries(CONNECTORS).map(([type, def]) => ({
    value: type as ConnectorType,
    label: def.label,
  }))

  const getLabel = (label: { label: string; labelHint: string | null }) => {
    const labelHint = label.labelHint ? `(${tCommon(`info.${label.labelHint}`)})` : ''
    return `${label.label} ${labelHint}`
  }

  return (
    <div>
      <ContentCard className={cn('h-full overflow-auto mb-6')} footerElement={<FooterElement />}>
        <SubHeader title={t('title1')} className="pb-4  border-b-1" />

        <FormSelect
          form={form}
          id="connector-type"
          label={t('type')}
          name="connector.type"
          options={connectorTypeOptions}
          formItemProps={{ className: 'py-6' }}
          required
        />
      </ContentCard>

      {config.length > 0 && (
        <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement />}>
          <SubHeader title={t('title2')} className="pb-4  border-b-1 mb-3" />
          {config.map(property => (
            <DynamicFormField<DatasourceFormDraft>
              key={property.key}
              form={form}
              label={getLabel(property.label)}
              name={`connector.config.${property.key}` as Path<DatasourceFormDraft>}
              placeholder={property.placeholder}
              type={property.type}
              options={property.options?.map(option => ({ value: option, label: option }))}
              shouldShowErrors={!isDraftMode}
              required={property.required}
              className="py-3"
            />
          ))}
        </ContentCard>
      )}
    </div>
  )
}
