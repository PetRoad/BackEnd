package com.walkmate.backend.domain;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="users") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class User { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String email; @Column(nullable=false) private String password; private String region; private LocalDateTime createdAt;
    public User(String email,String password,String region){this.email=email;this.password=password;this.region=region;this.createdAt=LocalDateTime.now();}
}
