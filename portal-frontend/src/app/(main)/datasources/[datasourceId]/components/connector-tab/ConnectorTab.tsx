import { useTranslations } from 'next-intl'
import { useMemo } from 'react'
import { Path, UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FooterElement } from '@/components/form/FooterElement'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { ConnectorType } from '@/types/connectors'
import { DatasourceFormDraft } from '@/types/datasources'

import { CONNECTOR_INPUTS } from './connectorSources'
import { DynamicFormField } from './DynamicFormField'

interface ConnectorTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  readyConnectorType?: ConnectorType
  isReadOnly?: boolean
}
export const ConnectorTab = (props: ConnectorTabProps) => {
  const { form, readyConnectorType, isReadOnly = false } = props
  const t = useTranslations('datasources.connectorTab')
  const tCommon = useTranslations('common')
  const connectorTypeOptions: SelectOption[] = Object.keys(CONNECTOR_INPUTS).map(type => ({
    value: type as ConnectorType,
    label: type,
  }))

  const connectorConfig = useMemo(
    () => (readyConnectorType ? CONNECTOR_INPUTS[readyConnectorType] : []),
    [readyConnectorType],
  )

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
          name="connectorType"
          options={connectorTypeOptions}
          formItemProps={{ className: 'py-6' }}
          required
          disabled={isReadOnly}
        />
      </ContentCard>

      {connectorConfig.length > 0 && (
        <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement />}>
          <SubHeader title={t('title2')} className="pb-4  border-b-1 mb-3" />
          {connectorConfig.map(property => (
            <DynamicFormField<DatasourceFormDraft>
              key={property.key}
              id={property.key}
              form={form}
              label={getLabel(property.label)}
              name={`configuration.${property.key}` as Path<DatasourceFormDraft>}
              placeholder={property.placeholder ?? ''}
              type={property.type}
              options={property.options?.map(option => ({ value: option, label: option }))}
              shouldShowErrors
              required={property.required}
              className="py-3"
              disabled={isReadOnly}
            />
          ))}
        </ContentCard>
      )}
    </div>
  )
}
