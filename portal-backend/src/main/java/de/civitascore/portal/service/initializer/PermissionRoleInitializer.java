package de.civitascore.portal.service.initializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionRoleInitializer implements ApplicationRunner {

  private final PermissionInitializer permissionInitializer;
  private final RoleInitializer roleInitializer;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    log.info("Starting permission and role initialization");
    permissionInitializer.initialize();
    roleInitializer.initialize();
    log.info("Permission and role initialization completed");
  }
}
