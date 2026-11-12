package com.petroad.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalkRoutePoint {
    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    public WalkRoutePoint(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }
}
