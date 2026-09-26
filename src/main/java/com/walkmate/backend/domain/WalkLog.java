package com.walkmate.backend.domain;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class WalkLog { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY) private User user; @ManyToOne(fetch=FetchType.LAZY) private Course course; private double distance; private LocalDateTime walkedAt; public WalkLog(User u,Course c,double distance){user=u;course=c;this.distance=distance;walkedAt=LocalDateTime.now();} }
