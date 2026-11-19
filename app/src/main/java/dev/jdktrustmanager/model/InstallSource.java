package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InstallSource {
    FOOJAY("foojay"),
    ADOPTED("adopted");

    private final String wire;

    InstallSource(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static InstallSource fromWire(String value) {
        for (InstallSource source : values()) {
            if (source.wire.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown install source: " + value);
    }
}
