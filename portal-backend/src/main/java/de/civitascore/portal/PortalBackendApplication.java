package de.civitascore.portal;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"de.civitascore.portal"})
@OpenAPIDefinition(
    servers = {@Server(url = "${app.url:http://localhost:8089}")},
    info = @Info(title = "CIVITAS/CORE Data Management API", version = "2.0.0"))
@EntityScan(basePackages = "de.civitascore.portal")
public class PortalBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(PortalBackendApplication.class, args);
  }
}
