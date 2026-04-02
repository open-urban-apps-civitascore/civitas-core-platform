package de.civitascore.portal.configuration;

import de.civitascore.portal.controller.exception.SecurityExceptionHandler;
import de.civitascore.portal.security.CustomJwtAuthenticationConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Spring Security configuration for the portal backend. Configures stateless JWT-based OAuth2
 * resource server authentication, CSRF disabled, configurable permit paths, and custom JWT
 * conversion for extracting user details.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true)
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  private final CustomJwtAuthenticationConverter customJwtConverter;
  private final SecurityProperties securityProperties;

  public SecurityConfig(
      CustomJwtAuthenticationConverter customJwtConverter, SecurityProperties securityProperties) {
    this.customJwtConverter = customJwtConverter;
    this.securityProperties = securityProperties;
  }

  /**
   * Configures the HTTP security filter chain with stateless session management, configurable
   * permit paths, JWT-based OAuth2 resource server, and custom exception handling.
   *
   * @param http the Spring Security HTTP configuration builder
   * @return the configured security filter chain
   * @throws Exception if security configuration fails
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        securityProperties.permitPaths().stream()
                            .map(PathPatternRequestMatcher.withDefaults()::matcher)
                            .toArray(PathPatternRequestMatcher[]::new))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(customJwtConverter)))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(new SecurityExceptionHandler())
                    .accessDeniedHandler(new SecurityExceptionHandler()));

    return http.build();
  }
}
