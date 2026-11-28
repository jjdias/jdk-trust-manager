package dev.jdktrustmanager.profiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActivationGateTest {

    @Test
    void secondStartIsRefusedUntilFinish() {
        ActivationGate gate = new ActivationGate();
        assertTrue(gate.tryStart());
        assertFalse(gate.tryStart());
        gate.finish();
        assertTrue(gate.tryStart());
    }

    @Test
    void finishIsIdempotent() {
        ActivationGate gate = new ActivationGate();
        gate.finish();
        gate.finish();
        assertTrue(gate.tryStart());
        gate.finish();
        gate.finish();
        assertTrue(gate.tryStart());
        assertFalse(gate.tryStart());
    }
}
