package de.civitascore.portal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract guard that ties the headers the backend TRUSTS from the gateway to the in-repo APISIX
 * gateway config. Every trusted header ({@link AllowedScopesFilter#HEADER_NAME} and {@link
 * AllowedScopesFilter#HEADER_NAME_POOL}) MUST be:
 *
 * <ul>
 *   <li>forwarded by OPA to the backend ({@code opa.send_headers_upstream}) — otherwise OPA's value
 *       is dropped and the scope/pool filtering silently does nothing, and
 *   <li>stripped from client requests at the gateway ({@code proxy-rewrite.headers.remove}) —
 *       otherwise a client-supplied value passes through and is trusted (authorization bypass).
 * </ul>
 *
 * <p>This catches the failure mode where a new trusted header is added on the backend/OPA side but
 * the gateway forward/strip lists are not kept in sync (review finding F1, dev side).
 *
 * <p>SCOPE: this guards the in-repo dev gateway config only. The PRODUCTION gateway config lives in
 * the {@code civitas-core-deployment} repo ({@code components/portal/apisix-plugins.yaml}) and must
 * be kept in sync separately — there is no in-repo guard for that (F1 remains a cross-repo
 * concern).
 */
@DisplayName("Trusted-header gateway contract (dev)")
class TrustedHeaderGatewayContractTest {

  /**
   * The headers OPA emits and the backend trusts; must be forwarded AND stripped by the gateway.
   */
  private static final List<String> TRUSTED_HEADERS =
      List.of(AllowedScopesFilter.HEADER_NAME, AllowedScopesFilter.HEADER_NAME_POOL);

  private static final Pattern LIST_ITEM = Pattern.compile("^\\s*-\\s*\"?([^\"\\s]+)\"?\\s*$");

  @Test
  @DisplayName("every trusted header is forwarded AND stripped by the dev APISIX gateway")
  void devGatewayForwardsAndStripsEveryTrustedHeader() throws IOException {
    String yaml = Files.readString(apisixConfig());

    assertThat(listItemsUnder(yaml, "send_headers_upstream:"))
        .as(
            "dev apisix.yaml opa.send_headers_upstream must forward every trusted header to the"
                + " backend, else OPA's value is dropped and filtering does nothing")
        .containsAll(TRUSTED_HEADERS);

    assertThat(listItemsUnder(yaml, "remove:"))
        .as(
            "dev apisix.yaml proxy-rewrite.headers.remove must strip every client-supplied trusted"
                + " header, else a spoofed value is trusted (authorization bypass)")
        .containsAll(TRUSTED_HEADERS);
  }

  @Test
  @DisplayName("the OIDC session secret is injected, never a committed literal (secure-by-default)")
  void devGatewaySessionSecretIsNeverCommitted() throws IOException {
    String yaml = Files.readString(apisixConfig());
    List<String> secrets = sessionSecretValues(yaml);

    assertThat(secrets)
        .as("apisix.yaml must declare an openid-connect session.secret (bearer_only=false)")
        .isNotEmpty();
    // With bearer_only=false the openid-connect plugin processes session cookies, so session.secret
    // is a real authentication credential. A committed literal (even a "REPLACE-ME" placeholder) is
    // runnable and readable from git, so it MUST be injected via the environment. Resolving an
    // unset
    // ${{...}} makes APISIX fail to start — secure-by-default.
    assertThat(secrets)
        .as(
            "every openid-connect session.secret must be injected via ${{OIDC_SESSION_SECRET}}, never"
                + " a committed literal — a hard-coded/placeholder key is an authentication"
                + " credential anyone can read from git (re-introduces the known-key bypass)")
        .allMatch(v -> v.equals("${{OIDC_SESSION_SECRET}}"));
  }

  /**
   * Values of every {@code secret:} key (the openid-connect session secret), trimmed and unquoted.
   */
  private static List<String> sessionSecretValues(String yaml) {
    List<String> values = new ArrayList<>();
    Matcher m = Pattern.compile("(?m)^\\s*secret:\\s*(.+?)\\s*$").matcher(yaml);
    while (m.find()) {
      String raw = m.group(1).strip();
      if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
        raw = raw.substring(1, raw.length() - 1);
      }
      values.add(raw);
    }
    return values;
  }

  /** Collects the YAML sequence items immediately following the first {@code key:} line. */
  private static List<String> listItemsUnder(String yaml, String key) {
    List<String> items = new ArrayList<>();
    boolean inBlock = false;
    for (String line : yaml.split("\\R")) {
      String trimmed = line.strip();
      if (!inBlock) {
        inBlock = trimmed.equals(key);
        continue;
      }
      Matcher item = LIST_ITEM.matcher(line);
      if (item.matches()) {
        items.add(item.group(1));
      } else if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        // skip blank lines and comments interleaved in the block
      } else {
        break; // a non-list, non-comment line ends the YAML sequence
      }
    }
    return items;
  }

  private static Path apisixConfig() {
    Path dir = Path.of("").toAbsolutePath();
    for (int i = 0; i < 8 && dir != null; i++) {
      Path candidate = dir.resolve("dev-environment/apisix/apisix_conf/apisix.yaml");
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException(
        "Could not locate dev-environment/apisix/apisix_conf/apisix.yaml from "
            + Path.of("").toAbsolutePath());
  }
}
