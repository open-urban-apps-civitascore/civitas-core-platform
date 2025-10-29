package de.civitascore.portal.model.output.summary;

import lombok.Data;

@Data
public class UserSummaryDTO {
  private String id;
  private String firstName;
  private String lastName;
  private String email;
}
