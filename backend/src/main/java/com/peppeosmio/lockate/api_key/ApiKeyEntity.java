package com.peppeosmio.lockate.api_key;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "api_key")
@Getter
@Setter
@NoArgsConstructor
public class ApiKeyEntity {

  public ApiKeyEntity(
      String secretHash, String label, LocalDateTime createdAt, LocalDateTime expiresAt) {
    this.secretHash = secretHash;
    this.label = label;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "secret_hash", nullable = false)
  private String secretHash;

  @Column(name = "label")
  private String label;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "last_validated")
  private LocalDateTime lastValidated;
}
