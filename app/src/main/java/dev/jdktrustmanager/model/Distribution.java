package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Distribution(
        String name,
        String apiParameter,
        boolean maintained,
        boolean available,
        boolean buildOfGraalvm,
        List<String> versions) {
}
