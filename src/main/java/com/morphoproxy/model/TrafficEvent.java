package com.morphoproxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrafficEvent {
    private String timestamp;
    private String path;
    private long durationMs;
    private int statusCode;
    private String destinationUri;
}