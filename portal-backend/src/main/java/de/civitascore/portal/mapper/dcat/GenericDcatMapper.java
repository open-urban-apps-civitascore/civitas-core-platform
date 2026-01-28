package de.civitascore.portal.mapper.dcat;

import de.civitascore.portal.model.annotations.JsonLDProperty;
import de.civitascore.portal.model.annotations.JsonLDResource;
import de.civitascore.portal.model.output.BaseOutputDTO;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

@Component
public class GenericDcatMapper<T extends BaseOutputDTO> extends DcatMapper<T>
    implements HttpMessageConverter<T> {

  @Override
  public Model toModel(T dto) {
    Model model = createEmptyModel();

    Class<?> clazz = dto.getClass();

    if (!clazz.isAnnotationPresent(JsonLDResource.class)) {
      // TODO decide on fallback behavior
      throw new IllegalArgumentException(
          "Class " + clazz.getName() + " is not annotated with @JsonLDResource");
    }

    JsonLDResource resourceAnnotation = clazz.getAnnotation(JsonLDResource.class);

    Resource dcatResource = model.createResource(dto.getId().toString());

    Resource rdfType = model.createResource(resourceAnnotation.value());
    dcatResource.addProperty(RDF.type, rdfType);

    processFieldsRecursively(model, dcatResource, dto, clazz);

    return model;
  }

  /** Recursively process fields from the current class and all its superclasses */
  private void processFieldsRecursively(
      Model model, Resource resource, Object dto, Class<?> clazz) {
    if (clazz == null || clazz == Object.class) {
      return;
    }

    processFieldsRecursively(model, resource, dto, clazz.getSuperclass());

    Field[] fields = clazz.getDeclaredFields();

    Stream.of(fields)
      .filter(field -> !field.getName().equals("id"))
      .forEach(field -> processField(model, resource, dto, field));
  }

  private void processField(Model model, Resource resource, Object dto, Field field) {
    JsonLDProperty propertyAnnotation = field.getAnnotation(JsonLDProperty.class);
        String propertyUri;

    if (propertyAnnotation != null) {
      propertyUri = propertyAnnotation.nameSpace() + propertyAnnotation.localName();
    } else {
      // For unannotated fields, use a default namespace to ensure valid URIs
      String defaultNamespace = "urn:field:" + dto.getClass().getSimpleName() + "#";
      propertyUri = defaultNamespace + field.getName();
    }

    Property property = model.createProperty(propertyUri);

    field.setAccessible(true);

    try {
      Object value = field.get(dto);

      switch (value) {
        case null -> {
          // Skip null values
        }
        case Collection<?> objects ->
            handleCollection(model, resource, property, objects, propertyAnnotation);
        case BaseOutputDTO baseOutputDTO ->
            handleNestedResource(model, resource, property, baseOutputDTO);
        default -> handleLiteralValue(model, resource, property, value, propertyAnnotation);
      }

    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to access field " + field.getName(), e);
    }
  }

  private void handleCollection(
      Model model,
      Resource resource,
      Property property,
      Collection<?> collection,
      @Nullable JsonLDProperty propertyAnnotation) {
    for (Object item : collection) {
      if (item == null) {
        continue;
      }

      if (item instanceof BaseOutputDTO) {
        handleNestedResource(model, resource, property, (BaseOutputDTO) item);
      } else {
        handleLiteralValue(model, resource, property, item, propertyAnnotation);
      }
    }
  }

  private void handleNestedResource(
      Model model, Resource parentResource, Property property, BaseOutputDTO nestedDto) {
    Resource nestedResource = model.createResource(nestedDto.getId().toString());
    parentResource.addProperty(property, nestedResource);

    Class<?> nestedClass = nestedDto.getClass();
    if (nestedClass.isAnnotationPresent(JsonLDResource.class)) {
      JsonLDResource nestedResourceAnnotation = nestedClass.getAnnotation(JsonLDResource.class);
      Resource nestedRdfType = model.createResource(nestedResourceAnnotation.value());
      nestedResource.addProperty(RDF.type, nestedRdfType);

      processFieldsRecursively(model, nestedResource, nestedDto, nestedClass);
    }
  }

  private void handleLiteralValue(
      Model model,
      Resource resource,
      Property property,
      Object value,
      @Nullable JsonLDProperty propertyAnnotation) {
    Literal literal;

    if (propertyAnnotation != null && !propertyAnnotation.language().isEmpty() && value instanceof String) {
      literal = model.createLiteral((String) value, propertyAnnotation.language());
    } else {
      literal = model.createTypedLiteral(value);
    }

    resource.addProperty(property, literal);
  }

  public String toJsonLd(Model model) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, model, Lang.JSONLD);
    return writer.toString();
  }

  @Override
  public boolean canRead(@NonNull Class<?> clazz, @Nullable MediaType mediaType) {
    return clazz.isAnnotationPresent(JsonLDResource.class);
  }

  @Override
  public boolean canWrite(@NonNull Class<?> clazz, @Nullable MediaType mediaType) {
    return false;
  }

  @Override
  @NonNull public List<MediaType> getSupportedMediaTypes() {
    return List.of(MediaType.parseMediaType("application/ld+json"));
  }

  @Override
  @NonNull public T read(@NonNull Class<? extends T> clazz, @NonNull HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    throw new UnsupportedOperationException("Reading JSON-LD is not yet implemented");
  }

  @Override
  public void write(
      @NonNull T dto, @Nullable MediaType contentType, @NonNull HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    String result = toJsonLd(toModel(dto));
    outputMessage.getBody().write(result.getBytes());
  }
}
