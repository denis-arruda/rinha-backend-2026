package dev.denisarruda.rinha;

import java.util.Map;

final class MccRisk {

    private static final float DEFAULT = 0.5f;

    private static final Map<String, Float> TABLE = Map.of(
            "5411", 0.15f,
            "5812", 0.30f,
            "5912", 0.20f,
            "5944", 0.45f,
            "7801", 0.80f,
            "7802", 0.75f,
            "7995", 0.85f,
            "4511", 0.35f,
            "5311", 0.25f,
            "5999", 0.50f
    );

    static float get(String mcc) {
        return TABLE.getOrDefault(mcc, DEFAULT);
    }
}
