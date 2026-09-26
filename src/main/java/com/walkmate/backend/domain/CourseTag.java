package com.walkmate.backend.domain;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CourseTag { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY) private Course course; private String tag; CourseTag(Course c,String tag){course=c;this.tag=tag;} }
