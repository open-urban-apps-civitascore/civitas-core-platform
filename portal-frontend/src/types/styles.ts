import z from 'zod'

const SLD_DOCTYPE_NOT_ALLOWED = 'datasets.overview.completion.apis.config.styles.validation.sldContentDoctypeNotAllowed'
const SLD_MALFORMED = 'datasets.overview.completion.apis.config.styles.validation.sldContentMalformed'

const SLD_PREDEFINED_XML_ENTITIES = new Set(['amp', 'lt', 'gt', 'quot', 'apos'])
const ENTITY_REFERENCE_REGEX = /&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z_][\w.:-]*);/g

/**
 * Strips comments, CDATA blocks, and processing instructions in a single left-to-right pass so a
 * "DOCTYPE"/entity-reference-looking substring that only appears inside them (never real markup)
 * can't produce a false positive in the checks below. Must be one pass, not one `.replace()` per
 * construct: stripping all comments first would let a `<!--`-like sequence inside a PI pair up
 * with an unrelated later `-->` and swallow real markup (e.g. a genuine DOCTYPE) in between.
 */
const stripCommentsAndCData = (xml: string): string =>
  xml.replace(/<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<\?[\s\S]*?\?>/g, '')

type DoctypeToken = 'none' | 'valid' | 'invalidCase'

/**
 * Detects a DOCTYPE declaration by plain-text matching rather than via the parsed DOM: happy-dom
 * (and some browsers) recognize `<!doctype` case-insensitively, which would wrongly classify a
 * lowercase declaration as a real DOCTYPE instead of malformed markup. Matching text also means a
 * document with recursive/exponential internal-subset entity declarations is never handed to a
 * parser for expansion.
 */
const classifyDoctypeToken = (xmlWithoutNoise: string): DoctypeToken => {
  if (/<!DOCTYPE\b/.test(xmlWithoutNoise)) return 'valid'
  if (/<!\s*doctype\b/i.test(xmlWithoutNoise)) return 'invalidCase'
  return 'none'
}

/**
 * happy-dom's XML parser doesn't enforce the "Entity Declared" well-formedness constraint — it
 * silently leaves references to entities it doesn't recognize untouched instead of erroring — so a
 * reference to anything other than the 5 predefined entities or a numeric character reference must
 * be flagged explicitly to match GeoServer/StAX.
 */
const hasUndeclaredEntityReference = (xmlWithoutNoise: string): boolean => {
  for (const match of xmlWithoutNoise.matchAll(ENTITY_REFERENCE_REGEX)) {
    const reference = match[1]
    if (!reference.startsWith('#') && !SLD_PREDEFINED_XML_ENTITIES.has(reference)) return true
  }
  return false
}

/**
 * Mirrors SldContentValidator.java's GeoServer boundary: rejects any SLD document that declares a
 * DOCTYPE, and any document that is not well-formed XML. Runs on the untrimmed raw value — a
 * leading byte-order-mark is whitespace as far as String.prototype.trim() is concerned, so it must
 * be inspected here, before this field's own .transform() strips it away. Blank content is left to
 * the required-field check, matching the backend's StringUtils.isBlank early return.
 */
const validateSldContent = (value: string, ctx: z.RefinementCtx): void => {
  if (!value.trim()) return

  if (value.charCodeAt(0) === 0xfeff) {
    ctx.addIssue({ code: 'custom', message: SLD_MALFORMED })
    return
  }

  const withoutNoise = stripCommentsAndCData(value)
  const doctypeToken = classifyDoctypeToken(withoutNoise)

  if (doctypeToken === 'invalidCase') {
    ctx.addIssue({ code: 'custom', message: SLD_MALFORMED })
    return
  }
  if (doctypeToken === 'valid') {
    ctx.addIssue({ code: 'custom', message: SLD_DOCTYPE_NOT_ALLOWED })
    return
  }

  // DOMParser never throws for malformed XML — per spec it always returns a Document, with a
  // <parsererror> element inserted for the failure. This is the standard cross-browser detection.
  const doc = new DOMParser().parseFromString(value, 'application/xml')
  const isMalformed = doc.getElementsByTagName('parsererror').length > 0 || hasUndeclaredEntityReference(withoutNoise)

  if (isMalformed) {
    ctx.addIssue({ code: 'custom', message: SLD_MALFORMED })
  }
}

export const StyleSchema = z.object({
  id: z.uuid(),
  datasetId: z.uuid(),
  name: z.string(),
  sldContent: z.string(),
  inUse: z.boolean(),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type Style = z.infer<typeof StyleSchema>

export const StyleInputSchema = z.object({
  name: z
    .string()
    .min(1, 'common.errors.required')
    .regex(/^[A-Za-z0-9_-]+$/, 'common.errors.invalidCharacters'),
  sldContent: z
    .string()
    .superRefine(validateSldContent)
    .transform(value => value.trim())
    .pipe(z.string().min(1, 'common.errors.required')),
})

export type StyleInput = z.infer<typeof StyleInputSchema>

export const StyleFormSchema = StyleInputSchema.extend({
  id: z.string(),
})

export type StyleFormData = z.infer<typeof StyleFormSchema>

export type UpdateStyleInput = {
  datasetId: string
  stilId: string
  style: StyleInput
}

export type DeleteStyleInput = {
  datasetId: string
  stilId: string
}
