package de.civitascore.portal.configuration;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kaczmarzyk.spring.data.jpa.web.SpecificationArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration that registers the {@link SpecificationArgumentResolver} for resolving
 * JPA Specification parameters in controller method signatures.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  /**
   * {@inheritDoc} Registers the {@link SpecificationArgumentResolver} for JPA Specification-based
   * controller parameters.
   */
  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new SpecificationArgumentResolver());
  }
}
