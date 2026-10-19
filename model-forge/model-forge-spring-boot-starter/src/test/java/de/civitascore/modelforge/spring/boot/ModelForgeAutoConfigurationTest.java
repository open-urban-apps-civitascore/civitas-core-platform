package de.civitascore.modelforge.spring.boot;

import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.persistence.postgres.PostgresArtifactRegistryClient;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ModelForgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ModelForgeAutoConfiguration.class))
        .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void createsFacadeWhenOperationsBeanExistsWithoutRegisteringWebBeans() {
        contextRunner
            .withBean(ModelForge.class, () -> mock(ModelForge.class))
            .run(context -> {
                assertThat(context).hasSingleBean(ModelForge.class);
                assertThat(context).doesNotHaveBean("securityFilterChain");
                assertThat(Arrays.stream(context.getBeanDefinitionNames()))
                    .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("controller"));
            });
    }

    @Test
    void backsPostgresRegistryWithHostInfrastructureWhenRequiredBeansExist() {
        contextRunner
            .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(XsdSchemaConverter.class, () -> (content, urnPrefix) -> java.util.Map.of())
            .run(context -> {
                assertThat(context).hasSingleBean(ArtifactRegistry.class);
                assertThat(context.getBean(ArtifactRegistry.class))
                    .isInstanceOf(PostgresArtifactRegistryClient.class);
            });
    }

    @Test
    void startsInMinimalHostContextWithDataSource() {
        contextRunner
            .withPropertyValues("model-forge.registry.migrations-enabled=false")
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * Regression test for a real ordering bug: {@code ModelForgeAutoConfiguration} was only
     * declared {@code after = FlywayAutoConfiguration.class}, not after
     * {@code JdbcClientAutoConfiguration}/{@code DataSourceTransactionManagerAutoConfiguration}.
     * Unlike the other tests above, which inject {@code JdbcClient}/{@code PlatformTransactionManager}
     * directly via {@code withBean} (bypassing Spring Boot's real autoconfiguration ordering
     * entirely), this test lets those beans come from the real autoconfiguration classes so a
     * regression of the ordering bug — which silently produced no {@code ArtifactRegistry}/
     * {@code ModelForge} bean, with no startup failure — is actually caught.
     */
    @Test
    void resolvesArtifactRegistryThroughRealAutoconfigurationOrdering() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                DataSourceTransactionManagerAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                JdbcClientAutoConfiguration.class,
                ModelForgeAutoConfiguration.class
            ))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("model-forge.registry.migrations-enabled=false")
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ArtifactRegistry.class);
                assertThat(context.getBean(ArtifactRegistry.class))
                    .isInstanceOf(PostgresArtifactRegistryClient.class);
                assertThat(context).hasSingleBean(ModelForge.class);
                assertThat(context).hasSingleBean(ModelForge.class);
            });
    }
}
