package dev.denisarruda.rinha;

final class Normalizer implements NormalizationConstants {

    record Input(
        double transactionAmount,
        int    installments,
        int    hourOfDay,
        int    dayOfWeek,
        double customerAvgAmount,
        int    txCount24h,
        boolean merchantIsKnown,
        String  mcc,
        double merchantAvgAmount,
        boolean isOnline,
        boolean cardPresent,
        double kmFromHome,
        boolean hasLastTransaction,
        double lastKmFromCurrent,
        double minutesSinceLastTransaction
    ) {}

    static float[] normalize(Input in) {
        float[] v = new float[14];
        v[0] = clamp(in.transactionAmount() / MAX_AMOUNT);
        v[1] = clamp(in.installments() / MAX_INSTALLMENTS);
        v[2] = clamp((in.transactionAmount() / in.customerAvgAmount()) / AMOUNT_VS_AVG_RATIO);
        v[3] = in.hourOfDay() / 23.0f;
        v[4] = in.dayOfWeek() / 6.0f;
        v[5] = in.hasLastTransaction() ? clamp(in.minutesSinceLastTransaction() / MAX_MINUTES) : -1.0f;
        v[6] = in.hasLastTransaction() ? clamp(in.lastKmFromCurrent() / MAX_KM) : -1.0f;
        v[7] = clamp(in.kmFromHome() / MAX_KM);
        v[8] = clamp(in.txCount24h() / MAX_TX_COUNT_24H);
        v[9]  = in.isOnline() ? 1.0f : 0.0f;
        v[10] = in.cardPresent() ? 1.0f : 0.0f;
        v[11] = in.merchantIsKnown() ? 0.0f : 1.0f;
        v[12] = MccRisk.get(in.mcc());
        v[13] = clamp(in.merchantAvgAmount() / MAX_MERCHANT_AVG_AMOUNT);
        return v;
    }

    private static float clamp(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
