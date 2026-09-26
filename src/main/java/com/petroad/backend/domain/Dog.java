package com.petroad.backend.domain;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDate;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Dog { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",unique=true) private User user; private String name,breed,profileImage; @Enumerated(EnumType.STRING) private DogSize size; private LocalDate birthDate;
    public Dog(User u,String n,String b,DogSize s,LocalDate d,String image){user=u;name=n;breed=b;size=s;birthDate=d;profileImage=image;} public void update(String n,String b,DogSize s,LocalDate d,String image){name=n;breed=b;size=s;birthDate=d;profileImage=image;}
}
