package dev.jdktrustmanager.profiles;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jdktrustmanager.model.Profile;
import java.util.List;

/** profiles.json. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfilesDocument(List<Profile> profiles) {
}
