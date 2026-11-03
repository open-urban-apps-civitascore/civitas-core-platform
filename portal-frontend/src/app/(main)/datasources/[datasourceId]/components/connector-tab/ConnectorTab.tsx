import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef } from 'react'
import { get, Path, UseFormReturn, useWatch } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FooterElement } from '@/components/form/FooterElement'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { InfoBox } from '@/components/text-box/TextBox'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { CONNECTOR_FIELD_REVALIDATION_RULES, ConnectorType } from '@/types/connectors'
import { ConnectorFieldLabel, ConnectorFieldOption, DatasourceFormDraft } from '@/types/datasources'

import { ConnectionTest } from './connection-test/ConnectionTest'
import { CONNECTOR_INPUTS } from './connectorSources'
import { DynamicFormField } from './DynamicFormField'

interface ConnectorTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  datasourceId: string
  connectorType?: ConnectorType
  isConnectorComplete: boolean
  isConnectorChanged: boolean
  isReadOnly?: boolean
  isDatasourceReleased?: boolean
}
export const ConnectorTab = (props: ConnectorTabProps) => {
  const {
    form,
    datasourceId,
    connectorType,
    isConnectorComplete,
    isConnectorChanged,
    isReadOnly = false,
    isDatasourceReleased = false,
  } = props
  const isDisabled = isReadOnly || isDatasourceReleased
  const t = useTranslations('datasources.connectorTab')
  const connectorTypeOptions: SelectOption[] = Object.keys(CONNECTOR_INPUTS).map(type => ({
    value: type as ConnectorType,
    label: t(`types.${type}`),
  }))

  const connectorConfig = useMemo(() => (connectorType ? CONNECTOR_INPUTS[connectorType] : []), [connectorType])

  // Cross-field re-validation (e.g. MQTT's `tls` <-> `urls` scheme check) is declared once per
  // connector type in CONNECTOR_FIELD_REVALIDATION_RULES, colocated with the schema that owns the
  // relationship (see `validateMqttSchemes` in types/connectors.ts). This component stays generic
  // over connector type: adding a new connector's rule never requires touching this file.
  const revalidationRules = connectorType ? CONNECTOR_FIELD_REVALIDATION_RULES[connectorType] : []

  const configuration = useWatch({ control: form.control, name: 'configuration' })
  const visibleFields = connectorConfig.filter(
    field => !field.visibleWhen || get(configuration, field.visibleWhen.key) === field.visibleWhen.value,
  )

  const watchedFieldValues: unknown[] = revalidationRules.map(rule => get(configuration, rule.watch))
  const previousWatchedFieldValues = useRef(watchedFieldValues)

  useEffect(() => {
    const previousValues = previousWatchedFieldValues.current
    previousWatchedFieldValues.current = watchedFieldValues
    const changedRules = revalidationRules.filter((_rule, index) => previousValues[index] !== watchedFieldValues[index])
    const paths = changedRules.flatMap(rule => rule.revalidate)
    if (paths.length) {
      void form.trigger(paths.map(path => `configuration.${path}` as Path<DatasourceFormDraft>))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(watchedFieldValues)])

  const getLabel = (label: ConnectorFieldLabel) => {
    const hint = label.hintKey ? `(${t(label.hintKey)})` : ''
    return `${t(label.labelKey)} ${hint}`.trim()
  }

  const toSelectOption = (option: ConnectorFieldOption): SelectOption =>
    typeof option === 'string' ? { value: option, label: option } : { value: option.value, label: t(option.labelKey) }

  return (
    <div>
      {isDatasourceReleased && !isReadOnly && (
        <div className="mb-6">
          <InfoBox text={t('availableInfo')} />
        </div>
      )}
      <ContentCard className={cn('h-full overflow-auto mb-6')} footerElement={<FooterElement />}>
        <SubHeader title={t('title1')} className="pb-4  border-b-1" />

        <FormSelect
          form={form}
          id="connector-type"
          label={t('type')}
          name="connectorType"
          options={connectorTypeOptions}
          placeholder={t('typePlaceholder')}
          formItemProps={{ className: 'py-6' }}
          required
          disabled={isDisabled}
        />
      </ContentCard>

      {connectorConfig.length > 0 && (
        <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement />}>
          <SubHeader title={t('title2')} className="pb-4  border-b-1 mb-3" />
          {visibleFields.map(property => (
            <DynamicFormField<DatasourceFormDraft>
              key={property.key}
              id={property.key}
              form={form}
              label={getLabel(property.label)}
              name={`configuration.${property.key}` as Path<DatasourceFormDraft>}
              placeholder={property.placeholderKey ? t(property.placeholderKey) : (property.placeholder ?? '')}
              type={property.type}
              inputType={property.inputType}
              autoComplete={property.autoComplete}
              normalize={property.normalize}
              accept={property.accept}
              options={property.options?.map(toSelectOption)}
              shouldShowErrors
              required={property.required}
              className="py-3"
              disabled={isDisabled}
            />
          ))}
          <ConnectionTest
            form={form}
            datasourceId={datasourceId}
            isConnectorComplete={isConnectorComplete}
            isConnectorChanged={isConnectorChanged}
          />
        </ContentCard>
      )}
    </div>
  )
}
