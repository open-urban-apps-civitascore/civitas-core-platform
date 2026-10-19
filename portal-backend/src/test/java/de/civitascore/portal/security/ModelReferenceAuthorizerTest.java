package de.civitascore.portal.security;

import static de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelReference.Kind.EMBEDDED_FLOATING;
import static de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelReference.Kind.EMBEDDED_PINNED;
import static de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelReference.Kind.LINKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelReference;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.PublishedStructureRepository;
import de.civitascore.portal.util.ModelReferenceNotAvailableException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ModelReferenceAuthorizerTest {

  private static final UUID HOST = UUID.randomUUID();
  private static final UUID BRAVO = UUID.randomUUID();
  private static final String BRAVO_URN = "urn:core:platform:civitas:datastructure:common:" + BRAVO;
  private static final String MEMBER =
      "urn:core:platform:civitas:element:common:" + UUID.randomUUID();
  private static final Map<String, Object> MODEL = Map.of("type", "object");
  private static final String STORED =
      "urn:core:platform:civitas:datastructure:common:" + HOST + ":1.0.0";

  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataStructureRepository dataStructureRepository;
  @Mock private PublishedStructureRepository publishedStructureRepository;
  @Mock private ScopeAccessAuthorizer scopeAccessAuthorizer;

  @InjectMocks private ModelReferenceAuthorizer authorizer;

  @BeforeEach
  void resolveIds() {
    lenient().when(modelRegistryGateway.modelIdOf(BRAVO_URN)).thenReturn(Optional.of(BRAVO));
  }

  private void adding(ModelReference... references) {
    when(modelRegistryGateway.addedForeignReferences(HOST, MODEL, null, STORED))
        .thenReturn(List.of(references));
  }

  private Throwable write() {
    return catchThrowable(() -> authorizer.authorizeWrite(HOST, MODEL, null, STORED));
  }

  @Test
  @DisplayName("A model that adds no foreign reference needs no data structure to be readable")
  void noForeignReferenceNeedsNoReadableDataStructure() {
    adding();

    assertThatCode(() -> authorizer.authorizeWrite(HOST, MODEL, null, STORED))
        .doesNotThrowAnyException();
    verify(scopeAccessAuthorizer).authorizeReadAccess(ScopeType.DATASTRUCTURE, Set.of());
  }

  @Test
  @DisplayName("A pinned reference to a data structure the caller may read is allowed")
  void pinnedReadableReferenceIsAllowed() {
    adding(new ModelReference(MEMBER, EMBEDDED_PINNED, BRAVO_URN));
    when(dataStructureRepository.existsById(BRAVO)).thenReturn(true);

    assertThatCode(() -> authorizer.authorizeWrite(HOST, MODEL, null, STORED))
        .doesNotThrowAnyException();
    verify(scopeAccessAuthorizer).authorizeReadAccess(ScopeType.DATASTRUCTURE, Set.of(BRAVO));
  }

  @Test
  @DisplayName("An x-core-ref link needs no version, only read access")
  void unpinnedLinkIsAllowed() {
    adding(new ModelReference(MEMBER, LINKED, BRAVO_URN));
    when(dataStructureRepository.existsById(BRAVO)).thenReturn(true);

    assertThatCode(() -> authorizer.authorizeWrite(HOST, MODEL, null, STORED))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("A published structure needs READ on no data structure")
  void publishedStructureNeedsNoReadableDataStructure() {
    String port = "urn:core:platform:civitas:datastructure:frost:" + UUID.randomUUID();
    adding(new ModelReference(MEMBER, EMBEDDED_PINNED, port));
    when(publishedStructureRepository.existsByLogicalUrn(port)).thenReturn(true);

    assertThatCode(() -> authorizer.authorizeWrite(HOST, MODEL, null, STORED))
        .doesNotThrowAnyException();
    verify(scopeAccessAuthorizer).authorizeReadAccess(ScopeType.DATASTRUCTURE, Set.of());
  }

  @Test
  @DisplayName("An embedded reference without a version is refused")
  void unpinnedEmbeddingIsRefused() {
    adding(new ModelReference(MEMBER, EMBEDDED_FLOATING, BRAVO_URN));

    assertThat(write()).isInstanceOf(ModelReferenceNotAvailableException.class);
    verify(scopeAccessAuthorizer, never()).authorizeReadAccess(any(), any());
  }

  @Test
  @DisplayName("Every refusal reads the same, so it reveals neither the target nor its existence")
  void everyRefusalReadsTheSame() {
    adding(new ModelReference(MEMBER, EMBEDDED_PINNED, null));
    Throwable unknown = write();

    adding(new ModelReference(MEMBER, EMBEDDED_PINNED, BRAVO_URN));
    when(dataStructureRepository.existsById(BRAVO)).thenReturn(false);
    Throwable noPortalOwner = write();

    when(dataStructureRepository.existsById(BRAVO)).thenReturn(true);
    doThrow(new AccessDeniedException("denied"))
        .when(scopeAccessAuthorizer)
        .authorizeReadAccess(ScopeType.DATASTRUCTURE, Set.of(BRAVO));
    Throwable unreadable = write();

    adding(new ModelReference(MEMBER, EMBEDDED_FLOATING, BRAVO_URN));
    Throwable unpinned = write();

    for (Throwable refusal : List.of(unknown, noPortalOwner, unreadable, unpinned)) {
      assertThat(refusal)
          .isInstanceOf(ModelReferenceNotAvailableException.class)
          .hasMessage(new ModelReferenceNotAvailableException().getMessage());
      assertThat(refusal.getMessage()).doesNotContain(MEMBER).doesNotContain(BRAVO.toString());
    }
  }

  @Test
  @DisplayName("Release checks every reference of the stored model")
  void releaseChecksTheStoredModel() {
    when(modelRegistryGateway.foreignReferencesOf(HOST, STORED))
        .thenReturn(List.of(new ModelReference(MEMBER, EMBEDDED_PINNED, BRAVO_URN)));
    when(dataStructureRepository.existsById(BRAVO)).thenReturn(true);
    doThrow(new AccessDeniedException("denied"))
        .when(scopeAccessAuthorizer)
        .authorizeReadAccess(ScopeType.DATASTRUCTURE, Set.of(BRAVO));

    assertThatThrownBy(() -> authorizer.authorizeStored(HOST, STORED))
        .isInstanceOf(ModelReferenceNotAvailableException.class);
    verify(modelRegistryGateway, never()).addedForeignReferences(any(), any(), any(), anyString());
  }
}
