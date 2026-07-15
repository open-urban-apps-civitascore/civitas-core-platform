package de.civitascore.modelforge.adminui.wicket;

import org.apache.wicket.protocol.http.WicketFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Wicket filter with the Spring Boot embedded servlet container. Wicket has no
 * official Spring Boot starter, so the filter is wired by hand instead of adding a
 * third-party starter dependency.
 */
@Configuration
public class WicketConfig {

    @Bean
    public FilterRegistrationBean<WicketFilter> wicketFilter() {
        var registration = new FilterRegistrationBean<WicketFilter>();
        registration.setFilter(new WicketFilter());
        registration.addUrlPatterns("/*");
        registration.addInitParameter("applicationClassName", AdminWicketApplication.class.getName());
        registration.addInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        registration.setName("wicket");
        return registration;
    }
}
