import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef } from 'react'
import { Path, UseFormReturn, useWatch } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FooterElement } from '@/components/form/FooterElement'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { ConnectorType } from '@/types/connectors'
import { DatasourceFormDraft } from '@/types/datasources'

import { CONNECTOR_INPUTS } from './connectorSources'
import { DynamicFormField } from './DynamicFormField'

interface ConnectorTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  connectorType?: ConnectorType
  isReadOnly?: boolean
}
export const ConnectorTab = (props: ConnectorTabProps) => {
  const { form, connectorType, isReadOnly = false } = props
  const t = useTranslations('datasources.connectorTab')
  const tCommon = useTranslations('common')
  const connectorTypeOptions: SelectOption[] = Object.keys(CONNECTOR_INPUTS).map(type => ({
    value: type as ConnectorType,
    label: type,
  }))

  const connectorConfig = useMemo(() => (connectorType ? CONNECTOR_INPUTS[connectorType] : []), [connectorType])

  const tlsWatch = useWatch({ control: form.control, name: 'configuration.tls' as Path<DatasourceFormDraft> })
  const isFirstTlsRender = useRef(true)

  useEffect(() => {
    if (isFirstTlsRender.current) {
      isFirstTlsRender.current = false
      return
    }
    if (connectorType === CONNECTOR_TYPES.MQTT) {
      void form.trigger('configuration.urls' as Path<DatasourceFormDraft>)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tlsWatch])

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
          placeholder={t('typePlaceholder')}
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
              inputType={property.inputType}
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
