package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class ModelMemberUnreachableException extends ProblemException {

  private final List<String> memberNames;

  public ModelMemberUnreachableException(List<String> memberNames) {
    this(
        "DataStructureVersion model has members that its root Element does not reach: "
            + String.join(", ", memberNames),
        memberNames);
  }

  private ModelMemberUnreachableException(String message, List<String> memberNames) {
    super(ProblemType.MODEL_MEMBER_UNREACHABLE, message, message);
    this.memberNames = List.copyOf(memberNames);
  }

  /**
   * The member names come from the model of the version the caller releases, which the caller can
   * already read.
   */
  @Override
  public Map<String, Object> properties() {
    return Map.of("memberNames", memberNames);
  }
}
