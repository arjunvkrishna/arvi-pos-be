package com.neram.pos.service;

import com.neram.pos.dto.BillRequestDTO;
import com.neram.pos.entity.Bill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Rules for a bill paid in more than one way (pure, no database). The parts must add up to the bill's net amount; a tip is separate. */
public final class SplitPayments {
    private SplitPayments() {}

    public static final int MAX_PARTS = 6;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    public record Part(Bill.PaymentMode mode, BigDecimal amount, String reference) {}

    private static String rupees(BigDecimal v) { return "₹" + v.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    /** Checks the parts against the net amount. A refused request says exactly what is wrong. */
    public static List<Part> validate(List<BillRequestDTO.PaymentPart> in, BigDecimal net) {
        if (in == null || in.isEmpty()) return List.of();
        if (in.size() > MAX_PARTS) throw new IllegalArgumentException("A bill can be paid in at most " + MAX_PARTS + " parts.");
        List<Part> out = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < in.size(); i++) {
            BillRequestDTO.PaymentPart p = in.get(i);
            Bill.PaymentMode mode;
            try { mode = Bill.PaymentMode.valueOf(p.getMode() == null ? "" : p.getMode().trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Payment " + (i + 1) + " has an unknown mode. Use CASH, UPI, CARD, GIFT_CARD or LOYALTY_POINTS."); }
            if (p.getAmount() == null || p.getAmount().signum() <= 0) throw new IllegalArgumentException("Payment " + (i + 1) + " must be more than zero.");
            BigDecimal amount = p.getAmount().setScale(2, RoundingMode.HALF_UP);
            String ref = p.getReference() == null || p.getReference().isBlank() ? null : p.getReference().trim().substring(0, Math.min(100, p.getReference().trim().length()));
            out.add(new Part(mode, amount, ref));
            sum = sum.add(amount);
        }
        BigDecimal netScaled = net.setScale(2, RoundingMode.HALF_UP);
        if (sum.subtract(netScaled).abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalArgumentException("The payments add up to " + rupees(sum) + " but the bill is " + rupees(netScaled)
                    + (sum.compareTo(netScaled) < 0 ? ". " + rupees(netScaled.subtract(sum)) + " is still unpaid." : ". " + rupees(sum.subtract(netScaled)) + " is too much."));
        }
        return out;
    }

    /** The mode that took the most money (the first one on a tie); it is what older screens show as the bill's payment mode. */
    public static Part primary(List<Part> parts) {
        Part best = parts.get(0);
        for (Part p : parts) if (p.amount().compareTo(best.amount()) > 0) best = p;
        return best;
    }

    /** "CASH ₹200.00 + UPI ₹100.00" */
    public static String summary(List<Part> parts) {
        StringBuilder b = new StringBuilder();
        for (Part p : parts) { if (b.length() > 0) b.append(" + "); b.append(p.mode().name()).append(' ').append(rupees(p.amount())); }
        return b.toString();
    }

    /** Total per mode (for the cash drawer and the payment split). */
    public static Map<Bill.PaymentMode, BigDecimal> byMode(List<Part> parts) {
        Map<Bill.PaymentMode, BigDecimal> m = new EnumMap<>(Bill.PaymentMode.class);
        for (Part p : parts) m.merge(p.mode(), p.amount(), BigDecimal::add);
        return m;
    }

    /** A paid bill was edited and its net amount changed: scale every part in proportion; the last part takes the rounding. */
    public static List<Part> rescale(List<Part> parts, BigDecimal newNet) {
        BigDecimal oldSum = parts.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal target = newNet.setScale(2, RoundingMode.HALF_UP);
        if (oldSum.signum() <= 0 || oldSum.compareTo(target) == 0) return parts;
        List<Part> out = new ArrayList<>(); BigDecimal used = BigDecimal.ZERO;
        for (int i = 0; i < parts.size(); i++) {
            BigDecimal amount = i == parts.size() - 1 ? target.subtract(used)
                    : parts.get(i).amount().multiply(target).divide(oldSum, 2, RoundingMode.HALF_UP);
            out.add(new Part(parts.get(i).mode(), amount, parts.get(i).reference()));
            used = used.add(amount);
        }
        return out;
    }
}
