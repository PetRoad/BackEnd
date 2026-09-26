package com.petroad.backend.domain;
import jakarta.persistence.*; import lombok.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CoursePoint { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY) private Course course; private double lat,lng; private int sequence; CoursePoint(Course c,double lat,double lng,int seq){course=c;this.lat=lat;this.lng=lng;sequence=seq;} }
