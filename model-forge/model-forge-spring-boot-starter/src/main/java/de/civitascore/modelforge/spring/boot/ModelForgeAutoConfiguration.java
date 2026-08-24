package de.civitascore.modelforge.spring.boot;

import tools.jackson.databind.ObjectMapper;
import de.civitascore.modelforge.application.ElementCommandService;
import de.civitascore.modelforge.application.ElementQueryService;
import de.civitascore.modelforge.application.EmbeddedModelForgeOperations;
import de.civitascore.modelforge.application.ReferenceExistenceValidator;
import de.civitascore.modelforge.application.SchemaImportService;
import de.civitascore.modelforge.application.SmartDataModelsService;
import de.civitascore.modelforge.application.ViewService;
import de.civitascore.modelforge.application.XRepositoryService;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import de.civitascore.modelforge.core.port.XRepositoryCatalog;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.integrations.RemoteSchemaFetcher;
import de.civitascore.modelforge.integrations.xrepository.XRepositoryClient;
import de.civitascore.modelforge.persistence.postgres.PostgresArtifactRegistryClient;
import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.validation.CoreSchemaValidator;
import de.civitascore.modelforge.validation.ModelValidator;
import de.civitascore.modelforge.xsd.XsdToJsonSchemaConverter;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Ordered explicitly after Flyway, JdbcClient and the JDBC transaction manager
 * autoconfigurations: without this, Spring Boot's unordered autoconfiguration sequencing can run
 * {@link #modelForgeArtifactRegistry} before {@link JdbcClient}/{@link PlatformTransactionManager}
 * beans exist, silently skipping {@code ArtifactRegistry} (and therefore the whole
 * {@link ModelForge} bean chain) with no startup error — it only surfaces as a runtime
 * "no ModelForge bean" failure in a real host application.
 *
 * <p>Every {@code @Bean} method name (= bean name) carries a {@code modelForge} prefix so the
 * starter's beans can never collide with a host-application bean of the same generic name
 * (e.g. a host's own {@code viewService}). Consumers match by TYPE
 * ({@code @ConditionalOnMissingBean}/constructor injection), so behaviour is unchanged.
 */
@AutoConfiguration(
    after = {
        FlywayAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JdbcClientAutoConfiguration.class
    },
    // By name: spring-boot-jackson is not a compile dependency of this starter. Ordering after the
    // host's Jackson autoconfiguration lets a Boot host's ObjectMapper win over the fallback below.
    afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
)
@ConditionalOnClass(ModelForge.class)
@EnableConfigurationProperties(ModelForgeProperties.class)
public class ModelForgeAutoConfiguration {

    private static final String SUPPORTED_SCHEMA = "model_forge";

    /**
     * Model Forge's internal JSON handling runs on Jackson 3 ({@code tools.jackson}), matching
     * the Spring Boot 4 default. A web host's auto-configured {@code ObjectMapper} wins (this
     * autoconfiguration is ordered after the host's Jackson autoconfiguration); this fallback
     * only kicks in for non-web hosts and tests that define no Jackson 3 mapper themselves.
     */
    @Bean
    @ConditionalOnMissingBean
    ObjectMapper modelForgeObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    UrnService modelForgeUrnService(ModelForgeProperties properties) {
        var urn = properties.urn();
        return new UrnService(urn.scope(), urn.owner(), urn.domain(), urn.defaultVersion());
    }

    @Bean
    @ConditionalOnMissingBean
    XsdSchemaConverter modelForgeXsdSchemaConverter(ObjectMapper mapper) {
        return new XsdToJsonSchemaConverter(mapper);
    }

    /**
     * Declared here — before any {@code @ConditionalOnBean(ArtifactRegistry.class)} consumer
     * below — on purpose. {@code @ConditionalOnBean} only sees bean <em>definitions</em> already
     * registered when a later {@code @Bean} method in the same {@code @Configuration} class is
     * being processed; declaring this after its consumers would silently produce no
     * {@code ArtifactRegistry} and therefore no {@code ModelForge} bean at all.
     */
    @Bean
    @ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class, XsdSchemaConverter.class})
    @ConditionalOnMissingBean
    ArtifactRegistry modelForgeArtifactRegistry(
        JdbcClient jdbc,
        PlatformTransactionManager transactionManager,
        ObjectMapper mapper,
        UrnService urns,
        XsdSchemaConverter xsdConverter,
        ModelForgeProperties properties
    ) {
        requireSupportedSchema(properties.registry());
        return new PostgresArtifactRegistryClient(
            jdbc,
            transactionManager,
            mapper,
            urns,
            xsdConverter
        );
    }

    /**
     * HTTP fetch for the schema-import paths, safe to enable unconditionally: every call runs the
     * {@code UrlGuard} SSRF check, and the only caller
     * ({@link #modelForgeSmartDataModelsService}) builds its URL from a fixed host — so there is no
     * unsafe default to gate behind a property.
     */
    @Bean
    @ConditionalOnMissingBean
    RemoteSchemaRepository modelForgeRemoteSchemaRepository(ObjectMapper mapper) {
        return new RemoteSchemaFetcher(mapper);
    }

    /**
     * XRepository (xOEV) catalog client. Same rationale as {@link #modelForgeRemoteSchemaRepository}:
     * the base URL is fixed/operator-configured, not client-supplied, so it is safe to enable
     * unconditionally rather than behind a property.
     */
    @Bean
    @ConditionalOnMissingBean
    XRepositoryCatalog modelForgeXRepositoryCatalog(
        ObjectMapper mapper,
        Environment environment
    ) {
        String baseUrl = environment.getProperty("model-forge.xrepository.base-url", "https://www.xrepository.de/api");
        return new XRepositoryClient(baseUrl, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    SchemaRefExtractor modelForgeSchemaRefExtractor() {
        return new SchemaRefExtractor();
    }

    @Bean
    @ConditionalOnBean(ArtifactRegistry.class)
    @ConditionalOnMissingBean
    DependencyGraphService modelForgeDependencyGraphService(ArtifactRegistry registry) {
        return new DependencyGraphService(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    ModelValidator modelForgeModelValidator() {
        return new ModelValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    CoreSchemaValidator modelForgeCoreSchemaValidator(ObjectMapper mapper) {
        return new CoreSchemaValidator(mapper);
    }

    @Bean
    @ConditionalOnBean({ArtifactRegistry.class, DependencyGraphService.class})
    @ConditionalOnMissingBean
    ReferenceExistenceValidator modelForgeReferenceExistenceValidator(
        ArtifactRegistry registry,
        SchemaRefExtractor refExtractor
    ) {
        return new ReferenceExistenceValidator(registry, refExtractor);
    }

    @Bean
    @ConditionalOnBean({ArtifactRegistry.class, DependencyGraphService.class})
    @ConditionalOnMissingBean
    SchemaImportService modelForgeSchemaImportService(
        ModelValidator validator,
        ObjectMapper mapper,
        ArtifactRegistry registry,
        UrnService urns,
        SchemaRefExtractor refExtractor,
        DependencyGraphService graph,
        ReferenceExistenceValidator refExistence,
        RemoteSchemaRepository remoteFetcher,
        CoreSchemaValidator coreSchemaValidator
    ) {
        return new SchemaImportService(
            validator,
            mapper,
            registry,
            urns,
            refExtractor,
            graph,
            refExistence,
            remoteFetcher,
            coreSchemaValidator
        );
    }

    @Bean
    @ConditionalOnBean(SchemaImportService.class)
    @ConditionalOnMissingBean
    SmartDataModelsService modelForgeSmartDataModelsService(SchemaImportService schemaImportService) {
        return new SmartDataModelsService(schemaImportService);
    }

    @Bean
    @ConditionalOnBean({XRepositoryCatalog.class, SchemaImportService.class})
    @ConditionalOnMissingBean
    XRepositoryService modelForgeXRepositoryService(
        XRepositoryCatalog catalog,
        XsdSchemaConverter xsdConverter,
        SchemaImportService schemaImportService,
        UrnService urns,
        ObjectMapper mapper
    ) {
        return new XRepositoryService(catalog, xsdConverter, schemaImportService, urns, mapper);
    }

    @Bean
    @ConditionalOnBean(ArtifactRegistry.class)
    @ConditionalOnMissingBean
    ElementQueryService modelForgeElementQueryService(ArtifactRegistry registry) {
        return new ElementQueryService(registry);
    }

    @Bean
    @ConditionalOnBean({ArtifactRegistry.class, DependencyGraphService.class, SchemaRefExtractor.class})
    @ConditionalOnMissingBean
    ElementCommandService modelForgeElementCommandService(
        ArtifactRegistry registry,
        DependencyGraphService graph,
        SchemaRefExtractor refExtractor
    ) {
        return new ElementCommandService(registry, graph, refExtractor);
    }

    @Bean
    @ConditionalOnBean({ArtifactRegistry.class, DependencyGraphService.class})
    @ConditionalOnMissingBean
    ViewService modelForgeViewService(ArtifactRegistry registry, DependencyGraphService graph, ObjectMapper mapper) {
        return new ViewService(registry, graph, mapper);
    }

    @Bean
    @ConditionalOnBean({
        SchemaImportService.class,
        ElementQueryService.class,
        ElementCommandService.class,
        ViewService.class,
        ModelValidator.class,
        CoreSchemaValidator.class,
        ReferenceExistenceValidator.class,
        DependencyGraphService.class,
        ArtifactRegistry.class,
        SmartDataModelsService.class,
        XRepositoryService.class
    })
    @ConditionalOnMissingBean
    ModelForge modelForge(
        SchemaImportService schemaImportService,
        ElementQueryService elementQueryService,
        ElementCommandService elementCommandService,
        ViewService viewService,
        ModelValidator modelValidator,
        CoreSchemaValidator coreSchemaValidator,
        ReferenceExistenceValidator referenceExistenceValidator,
        DependencyGraphService dependencyGraph,
        ArtifactRegistry registry,
        SmartDataModelsService smartDataModelsService,
        XRepositoryService xRepositoryService,
        UrnService urns
    ) {
        return new EmbeddedModelForgeOperations(
            schemaImportService,
            elementQueryService,
            elementCommandService,
            viewService,
            modelValidator,
            coreSchemaValidator,
            referenceExistenceValidator,
            dependencyGraph,
            registry,
            smartDataModelsService,
            xRepositoryService,
            urns
        );
    }

    /**
     * Warms the in-memory dependency graph (and the XSD namespace index) from the registry once
     * the host has finished infrastructure startup — Flyway has already run at this point.
     */
    @Bean
    @ConditionalOnBean(DependencyGraphService.class)
    @ConditionalOnMissingBean(name = "modelForgeGraphWarmup", search = SearchStrategy.CURRENT)
    ApplicationRunner modelForgeGraphWarmup(DependencyGraphService graph) {
        return args -> graph.rebuild();
    }

    @Bean(name = "modelForgeFlyway", initMethod = "migrate")
    @ConditionalOnClass(Flyway.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(name = "modelForgeFlyway", search = SearchStrategy.CURRENT)
    @ConditionalOnProperty(
        prefix = "model-forge.registry",
        name = "migrations-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    Flyway modelForgeFlyway(DataSource dataSource, ModelForgeProperties properties) {
        var registry = properties.registry();
        requireSupportedSchema(registry);
        return Flyway.configure()
            .dataSource(dataSource)
            .schemas(registry.schema())
            .defaultSchema(registry.schema())
            .table(registry.migrationTable())
            .locations("classpath:db/model-forge/migration")
            .baselineOnMigrate(true)
            // Baseline at the first migration's own version, mirroring portal-backend
            // (baseline-version: 1.0.0 against its V1_0_0__baseline.sql). baselineOnMigrate only
            // fires for a schema that exists and is NON-empty without a history table, i.e. one
            // whose tables are already present — and V1's CREATE TABLE statements are not
            // idempotent, so re-running it there would fail. Baselining at 1 skips it correctly.
            .baselineVersion("1")
            .load();
    }

    private static void requireSupportedSchema(ModelForgeProperties.Registry registry) {
        if (!SUPPORTED_SCHEMA.equals(registry.schema())) {
            throw new IllegalStateException(
                "Only schema '" + SUPPORTED_SCHEMA + "' is currently supported by Model Forge runtime SQL"
            );
        }
    }
}
