package com.network.gateway.cache;

import lombok.Data;

import java.io.Serializable;

@Data
public class AttackContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;
    private int confidence;
    private long duration;
    private int requestCount;
    private int uniqueUriCount;
    private int attackHistoryCount;
    private double peakRps;
    private String attackType;

    public AttackContext() {
    }

    public AttackContext(String ip) {
        this.ip = ip;
    }

    public int calculateIntensity() {
        int intensity = 0;

        intensity += Math.min(40, confidence * 0.4);

        long durationSeconds = duration / 1000;
        intensity += Math.min(30, (int)(durationSeconds / 10.0 * 3));

        intensity += Math.min(20, requestCount / 100 * 5);

        intensity += Math.min(10, uniqueUriCount * 2);

        return Math.min(100, intensity);
    }

    public boolean isHighIntensity() {
        return calculateIntensity() >= 70;
    }

    public boolean isMediumIntensity() {
        int intensity = calculateIntensity();
        return intensity >= 40 && intensity < 70;
    }

    public boolean isLowIntensity() {
        return calculateIntensity() < 40;
    }
}
