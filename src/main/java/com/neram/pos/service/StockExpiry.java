package com.neram.pos.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works out which goods are still on the shelf and when they expire, from what was received.
 * The stock on hand is taken to be the NEWEST deliveries (the oldest were used first), so no sale, issue or edit has to know about batches.
 * A delivery expires on its own expiry date, or, when it has none, on its invoice date plus the item's shelf life (if the item has one).
 */
public final class StockExpiry {
    private StockExpiry() {}

    public static final String EXPIRED = "EXPIRED";
    public static final String SOON = "SOON";
    public static final String OK = "OK";

    /** One delivery of an item: when it came, how many stock units, and its expiry date if the purchase line had one. */
    public record Receipt(Long invoiceId, String invoiceNumber, LocalDate receivedOn, double stockQty, LocalDate expiry, String batchNumber) {
        public Receipt(Long invoiceId, String invoiceNumber, LocalDate receivedOn, double stockQty, LocalDate expiry) {
            this(invoiceId, invoiceNumber, receivedOn, stockQty, expiry, null);
        }
    }

    /** Stock still on hand from one delivery, with its expiry. */
    public record Batch(Long invoiceId, String invoiceNumber, LocalDate receivedOn, LocalDate expiry, double quantity, long daysLeft, String status, String batchNumber) {
        public Batch(Long invoiceId, String invoiceNumber, LocalDate receivedOn, LocalDate expiry, double quantity, long daysLeft, String status) {
            this(invoiceId, invoiceNumber, receivedOn, expiry, quantity, daysLeft, status, null);
        }
    }

    /** The item's expiry picture: the earliest batch that matters, how much is affected, and the worst status. null status = nothing tracked. */
    public record Summary(LocalDate nextExpiry, String status, double affectedQuantity) {}

    public static List<Batch> onHand(double onHandQty, List<Receipt> receipts, Integer shelfLifeDays, int warnDays, LocalDate today) {
        List<Receipt> newestFirst = new ArrayList<>(receipts);
        newestFirst.sort(Comparator.comparing(Receipt::receivedOn, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Receipt::invoiceId, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        List<Batch> out = new ArrayList<>();
        double left = StockUnits.round3(onHandQty);
        for (Receipt r : newestFirst) {
            if (left <= 0.0005) break;
            double take = Math.min(left, r.stockQty());
            if (take <= 0) continue;
            left = StockUnits.round3(left - take);
            LocalDate expiry = r.expiry();
            if (expiry == null && shelfLifeDays != null && shelfLifeDays > 0 && r.receivedOn() != null) expiry = r.receivedOn().plusDays(shelfLifeDays);
            if (expiry == null) continue;
            long days = ChronoUnit.DAYS.between(today, expiry);
            out.add(new Batch(r.invoiceId(), r.invoiceNumber(), r.receivedOn(), expiry, StockUnits.round3(take), days, statusOf(days, warnDays), r.batchNumber()));
        }
        out.sort(Comparator.comparing(Batch::expiry));
        return out;
    }

    public static String statusOf(long daysLeft, int warnDays) {
        return daysLeft < 0 ? EXPIRED : daysLeft <= Math.max(0, warnDays) ? SOON : OK;
    }

    public static Summary summarise(List<Batch> batches) {
        if (batches.isEmpty()) return new Summary(null, null, 0.0);
        String worst = OK;
        double affected = 0;
        Batch first = null;
        for (Batch b : batches) {                                   // batches are sorted by expiry
            if (!OK.equals(b.status())) {
                if (first == null) first = b;
                affected += b.quantity();
                if (EXPIRED.equals(b.status())) worst = EXPIRED; else if (!EXPIRED.equals(worst)) worst = SOON;
            }
        }
        return new Summary((first != null ? first : batches.get(0)).expiry(), worst, StockUnits.round3(affected));
    }
}
