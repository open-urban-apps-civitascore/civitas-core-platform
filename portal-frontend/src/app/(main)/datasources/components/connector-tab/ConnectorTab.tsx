import { useTranslations } from 'next-intl'
import { Path, UseFormReturn } from 'react-hook-form'

import { FormSelect } from '@/components/form/fields/FormSelect'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SelectOption } from '@/types/common'
import { ConnectorType } from '@/types/connectors'
import { ConnectorField, DatasourceFormDraft } from '@/types/datasources'

import { NODE_DEFS } from './connector_sources'
import { DynamicFormField } from './DynamicFormField'

interface ConnectorTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  isDraftMode: boolean
  connectorType: ConnectorType | undefined
  config: ConnectorField[]
}
export const ConnectorTab = (props: ConnectorTabProps) => {
  const { form, config, isDraftMode } = props
  const t = useTranslations('datasources.connectorTab')
  const connectorTypeOptions: SelectOption[] = Object.entries(NODE_DEFS).map(([type, def]) => ({
    value: type as ConnectorType,
    label: def.label,
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
        <DynamicFormField<DatasourceFormDraft>
          key={property.key}
          form={form}
          label={property.label}
          name={`connector.config.${property.key}` as Path<DatasourceFormDraft>}
          placeholder={property.placeholder}
          type={property.type}
          options={property.options?.map(option => ({ value: option, label: option }))}
          shouldShowErrors={!isDraftMode}
        />
      ))}
    </div>
  )
}
