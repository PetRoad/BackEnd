package com.petroad.backend.domain;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime; import java.util.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class WalkLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) private User user;
 @ManyToOne(fetch=FetchType.LAZY) private Course course;
 // Legacy API stores kilometers; my-page DTOs expose meters.
 private double distance;
 private LocalDateTime walkedAt;
 private String title;
 private Integer durationSec;
 @ElementCollection
 @CollectionTable(name="walk_log_photos", joinColumns=@JoinColumn(name="walk_log_id"))
 @OrderColumn(name="sequence") @Column(name="photo_url",length=2048,nullable=false)
 private List<String> photos=new ArrayList<>();
 public WalkLog(User u,Course c,double distance){user=u;course=c;this.distance=distance;walkedAt=LocalDateTime.now(java.time.Clock.systemUTC());}
 public void setDetails(String title,Integer durationSec,List<String> photos){this.title=title;this.durationSec=durationSec;this.photos.clear();if(photos!=null)this.photos.addAll(photos);}
}
