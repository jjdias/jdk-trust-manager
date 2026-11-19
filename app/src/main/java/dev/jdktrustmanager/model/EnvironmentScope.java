package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EnvironmentScope {
    USER("user"),
    SYSTEM("system");

    private final String wire;

    EnvironmentScope(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static EnvironmentScope fromWire(String value) {
        for (EnvironmentScope scope : values()) {
            if (scope.wire.equals(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown environment scope: " + value);
    }
}
