package de.civitascore.portal.configuration;

import de.civitascore.portal.model.embedded.UserTitleType;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "local.init")
@Profile("local")
@Validated
@Getter
@Setter
@NoArgsConstructor
public class LocalInitProperties {

  private List<GroupEntry> groups = new ArrayList<>();
  private List<UserEntry> users = new ArrayList<>();

  @Getter
  @Setter
  public static class GroupEntry {
    private String name;
    private String roleName;
    private String description;
  }

  @Getter
  @Setter
  public static class UserEntry {
    private String firstName;
    private String lastName;
    private String email;
    private UserTitleType title = UserTitleType.OTHER;
    private List<String> groups = new ArrayList<>();
  }
}
