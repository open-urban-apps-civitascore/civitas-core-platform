package de.civitascore.portal.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Custom Page implementation for REST responses RestTemplate needs this to deserialize paginated
 * responses
 */
@JsonIgnoreProperties(
    ignoreUnknown = true,
    value = {"pageable"})
public class RestPage<T> extends PageImpl<T> {

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public RestPage(
      @JsonProperty("content") List<T> content,
      @JsonProperty("number") int number,
      @JsonProperty("size") int size,
      @JsonProperty("totalElements") long totalElements) {
    super(content, PageRequest.of(number, size), totalElements);
  }

  public RestPage(List<T> content) {
    super(content);
  }

  public RestPage() {
    super(new ArrayList<>());
  }
}
