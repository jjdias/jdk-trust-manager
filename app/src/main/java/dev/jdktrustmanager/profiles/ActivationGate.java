package dev.jdktrustmanager.profiles;

import java.util.concurrent.atomic.AtomicBoolean;

/** Lets one profile activation run at a time; a second one is refused until the first finishes. */
public final class ActivationGate {

    private final AtomicBoolean busy = new AtomicBoolean();

    /** True when the caller now owns the gate; false when another activation is still in progress. */
    public boolean tryStart() {
        return busy.compareAndSet(false, true);
    }

    /** Releases the gate. Safe to call more than once. */
    public void finish() {
        busy.set(false);
    }
}
