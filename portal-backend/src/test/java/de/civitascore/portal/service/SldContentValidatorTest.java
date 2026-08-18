package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import de.civitascore.portal.util.InvalidInputException;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The accept/reject boundary in {@link #geoServerBoundary()} was taken from GeoServer's own
 * style-upload endpoint, so each row documents an observed server response rather than an assumed
 * rule. A style GeoServer publishes must stay accepted here, and one it refuses must be rejected.
 */
class SldContentValidatorTest {

  private final SldContentValidator validator = new SldContentValidator();

  private static final String XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

  private enum Outcome {
    ACCEPTED,
    REJECTED_AS_DOCTYPE,
    REJECTED_AS_MALFORMED
  }

  private static String sld(String prolog) {
    return prolog
        + "<StyledLayerDescriptor version=\"1.0.0\" xmlns=\"http://www.opengis.net/sld\">"
        + "<NamedLayer><Name>probe</Name><UserStyle><FeatureTypeStyle><Rule>"
        + "<PointSymbolizer><Graphic><Mark><WellKnownName>circle</WellKnownName></Mark>"
        + "<Size>6</Size></Graphic></PointSymbolizer>"
        + "</Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>";
  }

  private static Stream<Arguments> geoServerBoundary() {
    return Stream.of(
        arguments("plain document with an XML declaration", sld(XML_DECL), Outcome.ACCEPTED),
        arguments("document without an XML declaration", sld(""), Outcome.ACCEPTED),
        arguments(
            "DOCTYPE inside a comment ahead of the root element",
            sld(XML_DECL + "<!-- <!DOCTYPE StyledLayerDescriptor> -->"),
            Outcome.ACCEPTED),
        arguments(
            "escaped DOCTYPE text inside an element",
            sld(XML_DECL).replace("<Name>probe</Name>", "<Name>&lt;!DOCTYPE evil&gt;</Name>"),
            Outcome.ACCEPTED),
        arguments(
            "bare DOCTYPE declaration",
            sld(XML_DECL + "<!DOCTYPE StyledLayerDescriptor>"),
            Outcome.REJECTED_AS_DOCTYPE),
        arguments(
            "DOCTYPE with an internal subset",
            sld(XML_DECL + "<!DOCTYPE StyledLayerDescriptor [ <!ENTITY ph \"v\"> ]>"),
            Outcome.REJECTED_AS_DOCTYPE),
        arguments(
            "DOCTYPE referencing an external DTD",
            sld(XML_DECL + "<!DOCTYPE StyledLayerDescriptor SYSTEM \"http://example.test/x.dtd\">"),
            Outcome.REJECTED_AS_DOCTYPE),
        arguments(
            "DOCTYPE after leading whitespace",
            sld(XML_DECL + "\n\n   <!DOCTYPE StyledLayerDescriptor>"),
            Outcome.REJECTED_AS_DOCTYPE),
        arguments(
            "lowercase doctype keyword, which is not valid XML markup",
            sld(XML_DECL + "<!doctype StyledLayerDescriptor>"),
            Outcome.REJECTED_AS_MALFORMED),
        arguments(
            "byte-order mark ahead of the XML declaration",
            "﻿" + sld(XML_DECL),
            Outcome.REJECTED_AS_MALFORMED),
        arguments(
            "unclosed root element",
            XML_DECL + "<StyledLayerDescriptor><NamedLayer>",
            Outcome.REJECTED_AS_MALFORMED),
        arguments("content that is not XML at all", "not xml", Outcome.REJECTED_AS_MALFORMED),
        arguments(
            "the five entities XML defines itself",
            sld(XML_DECL)
                .replace("<Name>probe</Name>", "<Name>&amp; &lt; &gt; &quot; &apos;</Name>"),
            Outcome.ACCEPTED),
        arguments(
            "a character reference",
            sld(XML_DECL).replace("<Name>probe</Name>", "<Name>&#65;</Name>"),
            Outcome.ACCEPTED),
        arguments(
            "a reference to an entity nothing declares, which GeoServer also refuses",
            sld(XML_DECL).replace("<Name>probe</Name>", "<Name>&undeclared;</Name>"),
            Outcome.REJECTED_AS_MALFORMED),
        arguments(
            "nested entity declarations that would expand on parsing",
            sld(
                XML_DECL
                    + "<!DOCTYPE StyledLayerDescriptor [ <!ENTITY a \"aaaaaaaa\">"
                    + " <!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;\">"
                    + " <!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;\"> ]>"),
            Outcome.REJECTED_AS_DOCTYPE));
  }

  @ParameterizedTest(name = "{0} → {2}")
  @MethodSource("geoServerBoundary")
  @DisplayName("Should mirror GeoServer's accept/reject boundary")
  void shouldMirrorGeoServerBoundary(String description, String content, Outcome expected) {
    switch (expected) {
      case ACCEPTED -> assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
      case REJECTED_AS_DOCTYPE ->
          assertThatThrownBy(() -> validator.validate(content))
              .isInstanceOf(InvalidInputException.class)
              .hasMessageContaining("DOCTYPE");
      case REJECTED_AS_MALFORMED ->
          assertThatThrownBy(() -> validator.validate(content))
              .isInstanceOf(InvalidInputException.class)
              .hasMessageContaining("not well-formed");
    }
  }

  @Nested
  @DisplayName("Error reporting")
  class ErrorReporting {

    @Test
    @DisplayName("Should report the offending field so a client can highlight it")
    void shouldReportField() {
      String content = sld(XML_DECL + "<!DOCTYPE StyledLayerDescriptor>");

      assertThatThrownBy(() -> validator.validate(content))
          .isInstanceOf(InvalidInputException.class)
          .extracting(e -> ((InvalidInputException) e).getResourceInfo())
          .isEqualTo("sldContent");
    }

    @Test
    @DisplayName("Should report line and column so the user can locate the fault")
    void shouldReportLocation() {
      assertThatThrownBy(() -> validator.validate(XML_DECL + "<StyledLayerDescriptor>"))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageMatching(".*line \\d+, column \\d+.*");
    }

    @Test
    @DisplayName("Should report the same message whatever locale the server runs in")
    void shouldReportLocaleIndependentMessage() {
      // The JDK localises its parser diagnostics, so echoing them would make this message differ
      // between deployments.
      Locale original = Locale.getDefault();
      try {
        Locale.setDefault(Locale.GERMANY);
        String german = messageOfMalformed();
        Locale.setDefault(Locale.US);
        String english = messageOfMalformed();

        assertThat(german).isEqualTo(english).doesNotContain("\n");
      } finally {
        Locale.setDefault(original);
      }
    }

    private String messageOfMalformed() {
      return catchThrowableOfType(
              InvalidInputException.class,
              () -> validator.validate(XML_DECL + "<StyledLayerDescriptor>"))
          .getMessage();
    }

    @Test
    @DisplayName("Should not resolve an external entity into the reported message")
    void shouldNotResolveExternalEntities() {
      String content =
          sld(
              XML_DECL
                  + "<!DOCTYPE StyledLayerDescriptor [ <!ENTITY x SYSTEM \"file:///etc/passwd\"> ]>");

      assertThatThrownBy(() -> validator.validate(content))
          .isInstanceOf(InvalidInputException.class)
          .extracting(Throwable::getMessage)
          .satisfies(message -> assertThat(message).doesNotContain("root:"));
    }
  }

  @Nested
  @DisplayName("Blank input")
  class BlankInput {

    @Test
    @DisplayName("Should leave blank content to the NotBlank constraint on the input DTO")
    void shouldIgnoreBlankContent() {
      assertThatCode(() -> validator.validate("  ")).doesNotThrowAnyException();
      assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }
  }
}
