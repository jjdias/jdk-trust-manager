package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Settings(
        String rootDirectory,
        EnvironmentScope environmentScope,
        long catalogMaxAgeHours,
        boolean hideMajorsBelow8,
        boolean hideUpstreamEndOfLife,
        boolean showEarlyAccess,
        boolean showGraalvm,
        boolean showJavafxBuilds,
        boolean pauseAutomaticTrustSync,
        boolean showBuildToolPreReleases) {
}
