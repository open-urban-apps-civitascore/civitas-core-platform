package de.civitascore.authz.repository;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EntityScan(basePackages = "de.civitascore.portal.model.entity")
public class AuthzRepositoryApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthzRepositoryApplication.class, args);
  }
}
