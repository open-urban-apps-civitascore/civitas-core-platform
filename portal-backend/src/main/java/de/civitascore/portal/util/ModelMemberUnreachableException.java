package de.civitascore.portal.util;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ModelMemberUnreachableException extends RuntimeException {

  private final List<String> memberNames;

  public ModelMemberUnreachableException(List<String> memberNames) {
    super(
        "DataStructureVersion model has members that its root Element does not reach: "
            + String.join(", ", memberNames));
    this.memberNames = List.copyOf(memberNames);
  }
}
