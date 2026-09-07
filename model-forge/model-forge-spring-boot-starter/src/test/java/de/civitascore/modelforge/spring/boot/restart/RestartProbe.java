package de.civitascore.modelforge.spring.boot.restart;

import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Optional;
import java.util.Set;

/**
 * A minimal host application, launched as its own process by {@code RegistryRestartTest}.
 *
 * <p>It exists because restart durability cannot be shown inside one JVM: a second application
 * context in the same process still sees whatever the first left in memory. Each mode below is one
 * process lifetime, so state that did not reach the database is genuinely gone by the time the next
 * mode runs.
 *
 * <p>Modes, selected by the first program argument:
 *
 * <ul>
 *   <li>{@code write} — stores an XSD Element whose {@code targetNamespace} enters the namespace
 *       index, then prints the URN it wrote.
 *   <li>{@code verify} — resolves the same namespace and reads the artifacts back. Exits non-zero
 *       with a reason when anything fails to resolve.
 * </ul>
 *
 * <p>Run {@code verify} with the {@code no-warmup} profile to suppress the startup graph rebuild
 * (see {@link NoWarmup}). Without that, the rebuild re-derives the namespace index from the stored
 * XSDs on every start, and the check would pass whether or not the index itself is durable.
 */
@SpringBootApplication
public class RestartProbe {

    static final String NAMESPACE = "urn:example:station";
    static final String ELEMENT_NAME = "StationXsd";

    /** Printed on the write run so the verifying run can be given the exact URN. */
    static final String WROTE_PREFIX = "PROBE-WROTE ";

    private static final String XSD =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                   targetNamespace="%s"
                   elementFormDefault="qualified">
          <xs:element name="Station" type="xs:string"/>
        </xs:schema>
        """.formatted(NAMESPACE);

    /** An XSD that imports the namespace above, so resolution has to consult the index. */
    private static final String IMPORTING_XSD =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                   targetNamespace="urn:example:reading"
                   elementFormDefault="qualified">
          <xs:import namespace="%s"/>
          <xs:element name="Reading" type="xs:string"/>
        </xs:schema>
        """.formatted(NAMESPACE);

    public static void main(String[] args) {
        SpringApplication.run(RestartProbe.class, args);
    }

    @Bean
    ApplicationRunner probe(ApplicationContext context, ArtifactRegistry registry, UrnService urns) {
        return args -> {
            String mode = args.getSourceArgs().length > 0 ? args.getSourceArgs()[0] : "";
            int status =
                switch (mode) {
                    case "write" -> write(registry, urns);
                    case "verify" -> verify(registry);
                    default -> fail("unknown probe mode: '" + mode + "'");
                };
            // Exit explicitly: the status is how the test observes the outcome of this process.
            SpringApplication.exit(context, () -> status);
            System.exit(status);
        };
    }

    private static int write(ArtifactRegistry registry, UrnService urns) {
        // storeXsdElement takes a URN, not a display name, and stores whatever it is given as the
        // logical identity — so the URN is minted here rather than passing ELEMENT_NAME.
        String xsdPin = registry.storeXsdElement(
            urns.mintElement(ELEMENT_NAME), XSD, Set.of(), null, VersionBump.PATCH);
        System.out.println(WROTE_PREFIX + xsdPin);
        return 0;
    }

    private static int verify(ArtifactRegistry registry) {
        // The namespace index is the state under test: resolving an xs:import consults it.
        Set<String> resolved = registry.extractImportRefs(IMPORTING_XSD);
        if (resolved.isEmpty()) {
            return fail("xs:import of " + NAMESPACE + " resolved to nothing after restart");
        }
        String target = resolved.iterator().next();

        Optional<?> content = registry.fetchElementOrXsd(target);
        if (content.isEmpty()) {
            return fail("the artifact the namespace resolved to is not readable: " + target);
        }
        if (!UrnParser.isUrn(target)) {
            return fail("namespace resolved to something that is not a CORE URN: " + target);
        }
        System.out.println("PROBE-RESOLVED " + target);
        return 0;
    }

    private static int fail(String reason) {
        System.err.println("PROBE-FAILED " + reason);
        return 1;
    }

    /**
     * Replaces the starter's startup graph rebuild with a no-op.
     *
     * <p>The auto-configuration backs off when a bean of this name already exists, so defining one
     * here suppresses the rebuild — and with it the rescan that would otherwise repopulate the
     * namespace index at startup.
     */
    @Profile("no-warmup")
    @org.springframework.context.annotation.Configuration
    static class NoWarmup {

        @Bean(name = "modelForgeGraphWarmup")
        ApplicationRunner modelForgeGraphWarmup() {
            return args -> {};
        }
    }
}
