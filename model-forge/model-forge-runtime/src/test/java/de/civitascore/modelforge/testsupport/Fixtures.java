package de.civitascore.modelforge.testsupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Fixtures {

    private Fixtures() {
    }

    public static String text(String path) {
        try (var in = new ClassPathResource("fixtures/" + path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read fixture: " + path, e);
        }
    }

    public static JsonNode json(ObjectMapper mapper, String path) {
        try {
            return mapper.readTree(text(path));
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse fixture: " + path, e);
        }
    }
}
