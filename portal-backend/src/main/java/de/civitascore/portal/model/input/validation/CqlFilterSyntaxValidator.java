package de.civitascore.portal.model.input.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.Expression;
import org.geotools.api.filter.expression.Function;
import org.geotools.filter.FilterFactoryImpl;
import org.geotools.filter.FunctionFinder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;

/**
 * Validator backing {@link CqlFilterSyntax}. The message names the parser's position but not its
 * wording, which lists internal grammar tokens.
 */
public class CqlFilterSyntaxValidator implements ConstraintValidator<CqlFilterSyntax, String> {

  /**
   * Longest filter this validator parses. The parser recurses per bracket and overflows a default
   * thread stack from about 600 nested brackets, so longer input is left to {@code @Size}.
   */
  public static final int MAX_LENGTH = 500;

  /**
   * The fault position: it starts a lexical error and ends the first line of a parse error.
   * Anchored so that a string literal the parser echoes cannot pose as the position.
   */
  private static final Pattern POSITION =
      Pattern.compile(
          "^Lexical error at line (\\d+), column (\\d+)\\.|at line (\\d+), column (\\d+)\\.\\R");

  private static final String END_OF_INPUT = "Encountered \"<EOF>\"";

  private static final String NOT_VALID = "CQL filter is not valid ECQL";

  private static final FilterFactory FILTER_FACTORY = new AnyFunctionFilterFactory();

  @Override
  public boolean isValid(String cqlFilter, ConstraintValidatorContext ctx) {
    if (cqlFilter == null || cqlFilter.isBlank() || cqlFilter.length() > MAX_LENGTH) {
      return true;
    }
    try {
      ECQL.toFilter(cqlFilter, FILTER_FACTORY);
      return true;
    } catch (CQLException e) {
      return reject(ctx, describe(e.getMessage()));
    } catch (RuntimeException e) {
      // Some malformed literals fail unchecked, e.g. a one-point LINESTRING; GeoServer fails too.
      return reject(ctx, NOT_VALID);
    }
  }

  private static String describe(String parserMessage) {
    if (parserMessage.startsWith(END_OF_INPUT)) {
      return "CQL filter is incomplete: a value, condition or closing bracket is missing at the end";
    }
    Matcher position = POSITION.matcher(parserMessage);
    if (!position.find()) {
      return NOT_VALID;
    }
    int line = position.group(1) != null ? 1 : 3;
    return NOT_VALID
        + " near line "
        + position.group(line)
        + ", column "
        + position.group(line + 1);
  }

  private static boolean reject(ConstraintValidatorContext ctx, String message) {
    // The message holds no user input, so it is safe as a message template.
    ctx.disableDefaultConstraintViolation();
    ctx.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    return false;
  }

  /**
   * Resolves an unknown function to a placeholder instead of failing the parse: GeoServer registers
   * functions GeoTools alone does not know, and their names are resolved once published.
   */
  private static final class AnyFunctionFilterFactory extends FilterFactoryImpl {

    private final FunctionFinder functionFinder = new FunctionFinder(null);

    @Override
    public Function function(String name, Expression... args) {
      return functionFinder.findFunction(name, Arrays.asList(args), literal(null));
    }
  }
}
