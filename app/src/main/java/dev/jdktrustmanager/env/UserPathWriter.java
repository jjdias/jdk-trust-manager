package dev.jdktrustmanager.env;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The one way the app changes the user environment: read the user Path from the registry at the moment of writing,
 * build the plan from that, and apply it. A screen's preview can be minutes old (another tab may have set a tool since),
 * so plans are never built from a cached Path. When the Path can't be read, nothing is written.
 */
public final class UserPathWriter {

    /** Registry access, replaceable in tests. */
    public interface Registry {
        /** The user Path; {@code ""} when the user has no Path value; empty when the registry couldn't be read. */
        Optional<String> userPath() throws IOException, InterruptedException;

        void apply(EnvironmentPlan plan) throws IOException, InterruptedException;
    }

    private final Registry registry;

    public UserPathWriter(Registry registry) {
        this.registry = registry;
    }

    public static UserPathWriter live() {
        return new UserPathWriter(new Registry() {
            @Override
            public Optional<String> userPath() throws IOException, InterruptedException {
                return pathFrom(RegistryEnvironment.readAll(RegistryEnvironment.USER));
            }

            @Override
            public void apply(EnvironmentPlan plan) throws IOException, InterruptedException {
                new UserEnvironment().apply(plan);
            }
        });
    }

    public EnvironmentPlan write(Function<String, EnvironmentPlan> build) throws IOException, InterruptedException {
        String current = registry.userPath()
                .orElseThrow(() -> new IOException("The user Path couldn't be read, so nothing was changed. Try again."));
        EnvironmentPlan plan = build.apply(current);
        registry.apply(plan);
        return plan;
    }

    /** The Path value from a full read of HKCU\Environment; empty when the read failed. */
    static Optional<String> pathFrom(Optional<Map<String, String>> values) {
        return values.map(map -> JavaVariables.find(map, "Path"));
    }
}
