package de.civitascore.portal.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Table(name = "schemas")
public class SchemaEntity {
  @Id @GeneratedValue private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String type;

  @Lob
  @Column(nullable = false)
  private String content;

  @Column(nullable = false)
  @CreatedDate
  private Instant createdAt = Instant.now();
}
