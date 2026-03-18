package de.civitascore.portal.configuration;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.embedded.UserTitleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
@ConfigurationProperties(prefix = "init")
@Profile("init")
@Validated
@Getter
@Setter
@NoArgsConstructor
public class InitProperties {

  private @Valid List<GroupEntry> groups = new ArrayList<>();
  private @Valid List<UserEntry> users = new ArrayList<>();

  @Getter
  @Setter
  public static class GroupEntry {
    @NotBlank private String name;
    private String roleName;
    private ScopeType scopeType;
    private String description;
  }

  @Getter
  @Setter
  public static class UserEntry {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank @Email private String email;
    private String externalId;
    private UserTitleType title = UserTitleType.OTHER;
    private String password;
    private List<String> groups = new ArrayList<>();
  }
}
