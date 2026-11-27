package dev.jdktrustmanager.profiles;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jdktrustmanager.model.Profile;
import java.util.List;

/**
 * profiles.json. {@code version} is the file format; a file written by a newer app (a higher version) is refused rather
 * than read and rewritten, so fields this build doesn't know are never silently dropped. 0 (absent) means version 1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfilesDocument(int version, List<Profile> profiles) {

    public static final int CURRENT = 1;
}
