package de.civitascore.modelforge.adminui.wicket;

import org.apache.wicket.Application;
import org.apache.wicket.protocol.http.WicketFilter;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * DEPLOYMENT or DEVELOPMENT. Wicket's own default is DEVELOPMENT, which renders a full stack
     * trace into the browser on any unhandled request; DEPLOYMENT keeps the generic error page.
     */
    @Value("${wicket.configuration:deployment}")
    private String wicketConfiguration;

    @Bean
    public FilterRegistrationBean<WicketFilter> wicketFilter() {
        var registration = new FilterRegistrationBean<WicketFilter>();
        registration.setFilter(new WicketFilter());
        registration.addUrlPatterns("/*");
        registration.addInitParameter("applicationClassName", AdminWicketApplication.class.getName());
        registration.addInitParameter(Application.CONFIGURATION, wicketConfiguration);
        registration.addInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        registration.setName("wicket");
        return registration;
    }
}
