import { StyleInputSchema } from './styles'

const XML_DECL = '<?xml version="1.0" encoding="UTF-8"?>'

const sld = (prolog: string) =>
  `${prolog}<StyledLayerDescriptor version="1.0.0" xmlns="http://www.opengis.net/sld">` +
  '<NamedLayer><Name>probe</Name><UserStyle><FeatureTypeStyle><Rule>' +
  '<PointSymbolizer><Graphic><Mark><WellKnownName>circle</WellKnownName></Mark>' +
  '<Size>6</Size></Graphic></PointSymbolizer>' +
  '</Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>'

const DOCTYPE_MESSAGE = 'datasets.overview.completion.apis.config.styles.validation.sldContentDoctypeNotAllowed'
const MALFORMED_MESSAGE = 'datasets.overview.completion.apis.config.styles.validation.sldContentMalformed'

const parse = (sldContent: string) => StyleInputSchema.safeParse({ name: 'valid-name', sldContent })

describe('StyleInputSchema sldContent — GeoServer boundary parity', () => {
  it.each([
    ['plain document with an XML declaration', sld(XML_DECL)],
    ['document without an XML declaration', sld('')],
    ['DOCTYPE inside a comment ahead of the root element', sld(`${XML_DECL}<!-- <!DOCTYPE StyledLayerDescriptor> -->`)],
    [
      'escaped DOCTYPE text inside an element',
      sld(XML_DECL).replace('<Name>probe</Name>', '<Name>&lt;!DOCTYPE evil&gt;</Name>'),
    ],
    [
      'the five entities XML defines itself',
      sld(XML_DECL).replace('<Name>probe</Name>', '<Name>&amp; &lt; &gt; &quot; &apos;</Name>'),
    ],
    ['a character reference', sld(XML_DECL).replace('<Name>probe</Name>', '<Name>&#65;</Name>')],
  ])('accepts: %s', (_description, content) => {
    expect(parse(content).success).toBe(true)
  })

  it.each([
    ['bare DOCTYPE declaration', sld(`${XML_DECL}<!DOCTYPE StyledLayerDescriptor>`)],
    ['DOCTYPE with an internal subset', sld(`${XML_DECL}<!DOCTYPE StyledLayerDescriptor [ <!ENTITY ph "v"> ]>`)],
    [
      'DOCTYPE referencing an external DTD',
      sld(`${XML_DECL}<!DOCTYPE StyledLayerDescriptor SYSTEM "http://example.test/x.dtd">`),
    ],
    ['DOCTYPE after leading whitespace', sld(`${XML_DECL}\n\n   <!DOCTYPE StyledLayerDescriptor>`)],
    [
      'nested entity declarations that would expand on parsing',
      sld(
        `${XML_DECL}<!DOCTYPE StyledLayerDescriptor [ <!ENTITY a "aaaaaaaa">` +
          ' <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">' +
          ' <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;"> ]>',
      ),
    ],
  ])('rejects as DOCTYPE: %s', (_description, content) => {
    const result = parse(content)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['sldContent'], message: DOCTYPE_MESSAGE }),
      )
    }
  })

  it.each([
    ['lowercase doctype keyword, which is not valid XML markup', sld(`${XML_DECL}<!doctype StyledLayerDescriptor>`)],
    ['byte-order mark ahead of the XML declaration', `﻿${sld(XML_DECL)}`],
    ['unclosed root element', `${XML_DECL}<StyledLayerDescriptor><NamedLayer>`],
    ['content that is not XML at all', 'not xml'],
    [
      'a reference to an entity nothing declares',
      sld(XML_DECL).replace('<Name>probe</Name>', '<Name>&undeclared;</Name>'),
    ],
    [
      'a malformed document whose only DOCTYPE text sits in a comment',
      `${XML_DECL}<!-- <!DOCTYPE StyledLayerDescriptor> --><StyledLayerDescriptor>`,
    ],
  ])('rejects as malformed: %s', (_description, content) => {
    const result = parse(content)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['sldContent'], message: MALFORMED_MESSAGE }),
      )
    }
  })
})

describe('StyleInputSchema sldContent — blank handling', () => {
  it('reports only the required error for blank content, not malformed', () => {
    const result = parse('   ')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toEqual([
        expect.objectContaining({ path: ['sldContent'], message: 'common.errors.required' }),
      ])
    }
  })
})

describe('StyleInputSchema sldContent — frontend-specific hardening', () => {
  it('does not flag ordinary leading whitespace (only a real BOM) as malformed', () => {
    expect(parse(`\n\n   ${sld(XML_DECL)}`).success).toBe(true)
  })

  it('trims the stored value for accepted content', () => {
    const result = parse(`  ${sld(XML_DECL)}  `)
    expect(result.success).toBe(true)
    if (result.success) {
      expect(result.data.sldContent).toBe(sld(XML_DECL))
    }
  })

  it('still detects a real DOCTYPE hidden behind a fake comment opener inside a processing instruction', () => {
    // A naive "strip all comments, then strip all CDATA" pass would let the `<!--`-like text inside
    // `<?pi <!-- ?>` pair up with the unrelated `<!-- n -->` further down, deleting everything
    // between them — including this DOCTYPE — and wrongly accept the document.
    const content = `${XML_DECL}<?pi <!-- ?><!DOCTYPE r SYSTEM "http://example.test/x.dtd"><!-- n --><r/>`
    const result = parse(content)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['sldContent'], message: DOCTYPE_MESSAGE }),
      )
    }
  })
})

// The "rejects as malformed" cases above are only as trustworthy as happy-dom's DOMParser, which is
// more lenient than a real browser's (e.g. it accepts a bare unescaped `&` or duplicate attributes
// that a real XML parser — and the backend's StAX parser — would reject as not well-formed). Every
// row that relies purely on the `parsererror` fallback rather than an explicit pre-check
// (classifyDoctypeToken, the BOM check, hasUndeclaredEntityReference) should be spot-checked against
// a real browser's DOMParser when touching this file, since the unit tests alone can't catch a
// regression there.
