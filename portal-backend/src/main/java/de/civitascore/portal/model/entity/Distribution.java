package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "distributions",
    indexes = {
      @Index(name = "idx_distribution_resource", columnList = "resource_id"),
      @Index(name = "idx_distribution_dataset", columnList = "dataset_id"),
      @Index(name = "idx_distribution_activity", columnList = "activity_id")
    })
@Getter
@Setter
public class Distribution extends BaseEntity {

  @Column(name = "access_url", columnDefinition = "TEXT")
  private String accessUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resource_id")
  private Resource resource;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id")
  private DataSet dataSet;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "activity_id")
  private Activity activity;
}
