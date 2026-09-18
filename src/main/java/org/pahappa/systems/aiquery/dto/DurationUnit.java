package org.pahappa.systems.aiquery.dto;

/**
 * Unit an aggregated duration (see {@link AggregationRequest#durationStartField()}/
 * {@link AggregationRequest#durationEndField()}) is expressed in. Defaults to {@link #DAYS}
 * when not specified.
 */
public enum DurationUnit {
    SECONDS,
    MINUTES,
    HOURS,
    DAYS
}
