package com.petroad.backend.domain;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Course { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) private User user; private String name,coverImageUrl; private double distance,startLat,startLng; @Enumerated(EnumType.STRING) private Difficulty difficulty; @Enumerated(EnumType.STRING) private DogSize dogSize; private int likeCount;
    @OneToMany(mappedBy="course",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("sequence ASC") private List<CoursePoint> points=new ArrayList<>(); @OneToMany(mappedBy="course",cascade=CascadeType.ALL,orphanRemoval=true) private List<CourseTag> tags=new ArrayList<>();
    public Course(User u,String n,String image,double distance,Difficulty d,DogSize size,double lat,double lng){user=u;name=n;coverImageUrl=image;this.distance=distance;difficulty=d;dogSize=size;startLat=lat;startLng=lng;}
    public void addPoint(double lat,double lng,int seq){points.add(new CoursePoint(this,lat,lng,seq));} public void addTag(String tag){tags.add(new CourseTag(this,tag));} public void changeLikeCount(int delta){likeCount+=delta;}
}
