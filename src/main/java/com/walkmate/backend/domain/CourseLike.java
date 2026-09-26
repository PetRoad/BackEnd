package com.walkmate.backend.domain;
import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="course_likes",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","course_id"})) @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CourseLike { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY) private User user; @ManyToOne(fetch=FetchType.LAZY) private Course course; public CourseLike(User u,Course c){user=u;course=c;} }
