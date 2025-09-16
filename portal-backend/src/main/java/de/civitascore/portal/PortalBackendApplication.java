package de.civitascore.portal;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"de.civitascore.portal"})
@OpenAPIDefinition(
    servers = {@Server(url = "${app.url:http://localhost:8089}")},
    info = @Info(title = "CIVITAS-Core Data Mgmt API", version = "1.0.0"))
public class PortalBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(PortalBackendApplication.class, args);
  }
}
