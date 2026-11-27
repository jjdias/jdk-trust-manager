package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A named toolchain: one JDK and, optionally, one Maven, Ant, and Gradle version. A null version is "not in this profile". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Profile(String name, JdkRef jdk, String maven, String ant, String gradle) {

    @JsonIgnore
    public String version(Tool tool) {
        return switch (tool) {
            case MAVEN -> maven;
            case ANT -> ant;
            case GRADLE -> gradle;
        };
    }
}
