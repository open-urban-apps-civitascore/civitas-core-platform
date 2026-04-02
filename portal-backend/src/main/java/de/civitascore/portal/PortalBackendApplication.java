package de.civitascore.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"de.civitascore.portal"})
@EntityScan(basePackages = "de.civitascore.portal")
public class PortalBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(PortalBackendApplication.class, args);
  }
}
