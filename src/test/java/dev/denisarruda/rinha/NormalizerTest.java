package dev.denisarruda.rinha;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizerTest {

    private static final float DELTA = 1e-5f;

    private static final String BODY = """
            {
              "id": "tx-1329056812",
              "transaction":      { "amount": 41.12, "installments": 2, "requested_at": "2026-03-11T18:45:53Z" },
              "customer":         { "avg_amount": 82.24, "tx_count_24h": 3, "known_merchants": ["MERC-003", "MERC-016"] },
              "merchant":         { "id": "MERC-016", "mcc": "5411", "avg_amount": 60.25 },
              "terminal":         { "is_online": false, "card_present": true, "km_from_home": 29.23 },
              "last_transaction": null
            }
            """;

    @Test
    void normalize() {
        var input    = FraudRequestParser.parse(BODY);
        var features = Normalizer.normalize(input);

        assertEquals(14, features.length);
        assertEquals(0.004112f,   features[0],  DELTA); // transaction.amount / max_amount
        assertEquals(0.16667f,    features[1],  DELTA); // installments / max_installments
        assertEquals(0.05f,       features[2],  DELTA); // (amount / customer.avg) / ratio
        assertEquals(0.78261f,    features[3],  DELTA); // hour(18) / 23
        assertEquals(0.33333f,    features[4],  DELTA); // Wednesday(2) / 6
        assertEquals(-1.0f,       features[5],  DELTA); // last_transaction null
        assertEquals(-1.0f,       features[6],  DELTA); // last_transaction null
        assertEquals(0.02923f,    features[7],  DELTA); // km_from_home / max_km
        assertEquals(0.15f,       features[8],  DELTA); // tx_count_24h / max_tx_count_24h
        assertEquals(0.0f,        features[9],  DELTA); // is_online = false
        assertEquals(1.0f,        features[10], DELTA); // card_present = true
        assertEquals(0.0f,        features[11], DELTA); // MERC-016 is known
        assertEquals(0.15f,       features[12], DELTA); // mcc "5411" risk
        assertEquals(0.006025f,   features[13], DELTA); // merchant.avg_amount / max_merchant_avg_amount
    }
}
