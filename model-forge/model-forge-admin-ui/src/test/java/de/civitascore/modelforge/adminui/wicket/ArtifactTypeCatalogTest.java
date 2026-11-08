package de.civitascore.modelforge.adminui.wicket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtifactTypeCatalogTest {

    @Test
    void ordersAllEightKnownTypes() {
        assertThat(ArtifactTypeCatalog.TYPE_ORDER).containsExactly(
            "element", "datastructure", "dataset", "mapping", "pipeline", "datasource", "datasink", "dashboard");
    }

    @Test
    void labelForAKnownTypeUsesTheCuratedPlural() {
        assertThat(ArtifactTypeCatalog.labelFor("datastructure")).isEqualTo("Data Structures");
        assertThat(ArtifactTypeCatalog.labelFor("element")).isEqualTo("Elements");
    }

    @Test
    void labelForAnUnknownTypeFallsBackToCapitalizedInput() {
        assertThat(ArtifactTypeCatalog.labelFor("widget")).isEqualTo("Widget");
    }

    @Test
    void labelForNullReturnsNullRatherThanThrowing() {
        assertThat(ArtifactTypeCatalog.labelFor(null)).isNull();
    }
}
