package digital.zil.hl.additional.resilience;

/**
 * Режим LAB16 для вызовов Core Service.
 */
public enum Lab16Mode {
    NONE,
    RETRY,
    CIRCUIT_BREAKER;

    public static Lab16Mode fromProperty(final String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        final String n = raw.trim().toUpperCase().replace('-', '_');
        if ("CIRCUITBREAKER".equals(n)) {
            return CIRCUIT_BREAKER;
        }
        try {
            return valueOf(n);
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
