import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { NODE_DEFS } from './connector_sources'
import { useTransition } from 'react'
import { useTranslations } from 'next-intl'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { UseFormReturn } from 'react-hook-form'
import { ConnectorConfig, ConnectorType, DatasourceBaseFormData, DatasourceFormData } from '@/types/datasources'
import { SelectOption } from '@/types/common'
import { FormPropertyField } from './FormPropertyField'

interface ConnectorTabProps {
  form: UseFormReturn<DatasourceFormData>
  isDraftMode: boolean
  config: ConnectorConfig[]
}
export const ConnectorTab = (props: ConnectorTabProps) => {
  const { form, config } = props
  const t = useTranslations('datasources.connectorTab')
  const connectorTypes = Object.keys(NODE_DEFS) as ConnectorType[]
  const connectorTypeOptions: SelectOption[] = connectorTypes.map(type => ({
    value: type,
    label: NODE_DEFS[type].label,
  }))

  return (
    <div>
      <SubHeader title={t('title1')} />
      <FormSelect
        form={form}
        id="connector-type"
        label={t('type')}
        name="connector.type"
        options={connectorTypeOptions}
      />

      <SubHeader title={t('title2')} />
      {config.map(property => (
        <FormPropertyField
          key={property.key}
          form={form}
          label={property.label}
          name={`connector.config.${property.key}`}
          placeholder={property.placeholder}
          type={property.type}
          options={property.options?.map(option => ({ value: option, label: option }))}
        />
      ))}
    </div>
  )
}
