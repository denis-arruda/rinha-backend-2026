package dev.denisarruda.rinha;

import java.time.Instant;
import java.time.LocalDate;

final class FraudRequestParser {

    static Normalizer.Input parse(String body) {
        String transaction = object(body, "transaction");
        String customer    = object(body, "customer");
        String merchant    = object(body, "merchant");
        String terminal    = object(body, "terminal");
        String lastTx      = object(body, "last_transaction");

        double  txAmount       = number(transaction, "amount");
        int     installments   = (int) number(transaction, "installments");
        String  requestedAt    = string(transaction, "requested_at");
        int     hourOfDay      = Integer.parseInt(requestedAt.substring(11, 13));
        int     dayOfWeek      = LocalDate.parse(requestedAt.substring(0, 10)).getDayOfWeek().getValue() - 1;

        double  customerAvg    = number(customer, "avg_amount");
        int     txCount24h     = (int) number(customer, "tx_count_24h");
        String  knownMerchants = array(customer, "known_merchants");

        String  merchantId     = string(merchant, "id");
        String  mcc            = string(merchant, "mcc");
        double  merchantAvg    = number(merchant, "avg_amount");

        boolean isOnline       = bool(terminal, "is_online");
        boolean cardPresent    = bool(terminal, "card_present");
        double  kmFromHome     = number(terminal, "km_from_home");

        boolean hasLastTx      = lastTx != null;
        double  lastKm         = hasLastTx ? number(lastTx, "km_from_current") : 0.0;
        double  minutesSince   = hasLastTx
                ? minutesBetween(string(lastTx, "timestamp"), requestedAt)
                : 0.0;

        boolean merchantIsKnown = knownMerchants.contains("\"" + merchantId + "\"");

        return new Normalizer.Input(
                txAmount, installments, hourOfDay, dayOfWeek,
                customerAvg, txCount24h, merchantIsKnown,
                mcc, merchantAvg,
                isOnline, cardPresent, kmFromHome,
                hasLastTx, lastKm, minutesSince);
    }

    private static String object(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int colon = json.indexOf(':', keyIdx);
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (json.charAt(start) != '{') return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(start, i + 1);
        }
        return null;
    }

    private static double number(String obj, String key) {
        int start = valueStart(obj, key);
        int end = start;
        while (end < obj.length() && ",}\n\r ".indexOf(obj.charAt(end)) < 0) end++;
        return Double.parseDouble(obj.substring(start, end).trim());
    }

    private static boolean bool(String obj, String key) {
        return obj.startsWith("true", valueStart(obj, key));
    }

    private static String string(String obj, String key) {
        int open  = obj.indexOf('"', valueStart(obj, key));
        int close = obj.indexOf('"', open + 1);
        return obj.substring(open + 1, close);
    }

    private static String array(String obj, String key) {
        int open  = obj.indexOf('[', obj.indexOf("\"" + key + "\""));
        int close = obj.indexOf(']', open);
        return obj.substring(open, close + 1);
    }

    private static int valueStart(String obj, String key) {
        String search = "\"" + key + "\"";
        int colon = obj.indexOf(':', obj.indexOf(search) + search.length());
        int start = colon + 1;
        while (start < obj.length() && Character.isWhitespace(obj.charAt(start))) start++;
        return start;
    }

    private static double minutesBetween(String from, String to) {
        return (Instant.parse(to).toEpochMilli() - Instant.parse(from).toEpochMilli()) / 60_000.0;
    }
}
