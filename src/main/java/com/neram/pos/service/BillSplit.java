package com.neram.pos.service;

import com.neram.pos.entity.Bill;
import com.neram.pos.entity.BillItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Divides one bill into several customer bills by items (a group that ate together and wants separate bills).
 *
 * Print-only: the bill itself, its payments, stock and reports are untouched. Every rupee is accounted for: the discount,
 * promotion, parcel charge and GST are shared out in proportion to each bill's items, and the round-off is chosen so that the
 * net amounts of the new bills add up to the original net amount exactly (to the paisa, or to the rupee when the original
 * bill is rounded to whole rupees).
 */
public final class BillSplit {
    private BillSplit() {}

    public static final int MAX_PORTIONS = 6;
    private static final BigDecimal PAISA = new BigDecimal("0.01");
    private static final BigDecimal RUPEE = BigDecimal.ONE;

    /** {@code quantity} units of one line of the bill go on a new bill. */
    public record Share(Long billItemId, int quantity) {}

    public record PortionItem(Long billItemId, String itemName, int quantity, BigDecimal unitPrice, BigDecimal subtotal, boolean takeaway) {}

    public record Portion(int number, List<PortionItem> items, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal promotionDiscountAmount,
                          BigDecimal couponDiscount, BigDecimal parcelCharge, BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal taxAmount,
                          BigDecimal roundOff, BigDecimal netAmount) {}

    /** What a printed or paid bill can be split into: one list of shares per new bill, covering every unit of every line exactly once. */
    public static List<Portion> divide(Bill bill, List<List<Share>> request) {
        if (bill.getStatus() != Bill.Status.BILL_PRINTED && bill.getStatus() != Bill.Status.PAID) {
            throw new IllegalArgumentException("Only a printed or paid bill can be split.");
        }
        if (request == null || request.size() < 2) throw new IllegalArgumentException("Split the bill into at least 2 bills.");
        if (request.size() > MAX_PORTIONS) throw new IllegalArgumentException("A bill can be split into at most " + MAX_PORTIONS + " bills.");

        Map<Long, BillItem> lines = new LinkedHashMap<>();
        if (bill.getItems() != null) for (BillItem i : bill.getItems()) if (i.getQuantity() != null && i.getQuantity() > 0) lines.put(i.getId(), i);

        int n = request.size();
        Map<Long, int[]> qtyByLine = new LinkedHashMap<>();
        for (Long id : lines.keySet()) qtyByLine.put(id, new int[n]);
        for (int p = 0; p < n; p++) {
            for (Share s : request.get(p) == null ? List.<Share>of() : request.get(p)) {
                if (s == null || s.quantity() == 0) continue;
                int[] per = qtyByLine.get(s.billItemId());
                if (per == null) throw new IllegalArgumentException("That item is not on this bill.");
                if (s.quantity() < 0) throw new IllegalArgumentException("Quantities cannot be negative.");
                BillItem line = lines.get(s.billItemId());
                // checked in long: two huge quantities must not wrap around to the ordered quantity and pass as "everything placed"
                if ((long) per[p] + s.quantity() > line.getQuantity()) {
                    throw new IllegalArgumentException("More of " + line.getItemName() + " was placed than was ordered (" + line.getQuantity() + ").");
                }
                per[p] += s.quantity();
            }
        }
        for (Map.Entry<Long, int[]> e : qtyByLine.entrySet()) {
            BillItem line = lines.get(e.getKey());
            long placed = 0;
            for (int q : e.getValue()) placed += q;
            if (placed != line.getQuantity()) {
                throw new IllegalArgumentException(placed < line.getQuantity()
                        ? "Every item must go on one of the bills: " + (line.getQuantity() - placed) + " x " + line.getItemName() + " is not placed."
                        : "More of " + line.getItemName() + " was placed than was ordered (" + line.getQuantity() + ").");
            }
        }

        // each line's amount follows its quantity, to the paisa
        List<List<PortionItem>> items = new ArrayList<>();
        BigDecimal[] itemsTotal = new BigDecimal[n];
        for (int p = 0; p < n; p++) { items.add(new ArrayList<>()); itemsTotal[p] = BigDecimal.ZERO; }
        for (Map.Entry<Long, int[]> e : qtyByLine.entrySet()) {
            BillItem line = lines.get(e.getKey());
            int[] per = e.getValue();
            BigDecimal[] weights = new BigDecimal[n];
            for (int p = 0; p < n; p++) weights[p] = BigDecimal.valueOf(per[p]);
            BigDecimal[] amounts = allocate(nz(line.getSubtotal()), weights, PAISA);
            for (int p = 0; p < n; p++) {
                if (per[p] == 0) continue;
                items.get(p).add(new PortionItem(line.getId(), line.getItemName(), per[p], nz(line.getPrice()), amounts[p], Boolean.TRUE.equals(line.getIsTakeaway())));
                itemsTotal[p] = itemsTotal[p].add(amounts[p]);
            }
        }
        for (int p = 0; p < n; p++) if (items.get(p).isEmpty()) throw new IllegalArgumentException("Bill " + (p + 1) + " has no items. Put at least one item on every bill.");

        boolean gst = Boolean.TRUE.equals(bill.getGstEnabled());
        BigDecimal tax = nz(bill.getTaxAmount());
        BigDecimal cgst = !gst ? BigDecimal.ZERO : bill.getCgstAmount() != null ? bill.getCgstAmount() : tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal sgst = !gst ? BigDecimal.ZERO : bill.getSgstAmount() != null ? bill.getSgstAmount() : tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        BigDecimal[] discount = allocate(nz(bill.getDiscountAmount()), itemsTotal, PAISA);
        BigDecimal[] promo = allocate(nz(bill.getPromotionDiscountAmount()), itemsTotal, PAISA);
        BigDecimal[] coupon = allocate(nz(bill.getCouponDiscount()), itemsTotal, PAISA);
        BigDecimal[] parcel = allocate(nz(bill.getParcelCharge()), itemsTotal, PAISA);
        BigDecimal[] cgstShare = allocate(cgst, itemsTotal, PAISA);
        BigDecimal[] sgstShare = allocate(sgst, itemsTotal, PAISA);

        BigDecimal[] before = new BigDecimal[n];
        for (int p = 0; p < n; p++) {
            before[p] = itemsTotal[p].subtract(discount[p]).subtract(promo[p]).subtract(coupon[p]).add(parcel[p]).add(cgstShare[p]).add(sgstShare[p]);
        }
        BigDecimal net = bill.getNetAmount() != null ? bill.getNetAmount() : nz(bill.getSubtotal());
        boolean roundedToRupees = nz(bill.getRoundOff()).abs().compareTo(new BigDecimal("0.001")) > 0 && net.stripTrailingZeros().scale() <= 0;
        BigDecimal[] nets = allocate(net, before, roundedToRupees ? RUPEE : PAISA);

        List<Portion> out = new ArrayList<>();
        for (int p = 0; p < n; p++) {
            out.add(new Portion(p + 1, items.get(p), itemsTotal[p], discount[p], promo[p], coupon[p], parcel[p], cgstShare[p], sgstShare[p],
                    cgstShare[p].add(sgstShare[p]), nets[p].subtract(before[p]).setScale(2, RoundingMode.HALF_UP), nets[p]));
        }
        return out;
    }

    /** A bill that exists only to be printed: the original's header and the portion's items and amounts. It is never saved. */
    public static Bill asBill(Bill original, Portion portion) {
        Map<Long, BillItem> source = new LinkedHashMap<>();
        if (original.getItems() != null) for (BillItem i : original.getItems()) source.put(i.getId(), i);
        Bill b = new Bill();
        b.setId(original.getId());
        b.setBillNumber(original.getBillNumber());
        b.setTableNumber(original.getTableNumber());
        b.setCustomerName(original.getCustomerName());
        b.setCustomerId(original.getCustomerId());
        b.setOrderType(original.getOrderType());
        b.setOutdoorToken(original.getOutdoorToken());
        b.setTakeawayToken(original.getTakeawayToken());
        b.setGstEnabled(original.getGstEnabled());
        b.setGstin(original.getGstin());
        b.setCgstPercent(original.getCgstPercent());
        b.setSgstPercent(original.getSgstPercent());
        b.setTaxPercent(original.getTaxPercent());
        b.setDiscountPercent(original.getDiscountPercent());
        b.setPromotionName(original.getPromotionName());
        b.setPromotionPercentage(original.getPromotionPercentage());
        b.setCreatedBy(original.getCreatedBy());
        b.setPrintedBy(original.getPrintedBy());
        b.setCreatedAt(original.getCreatedAt());
        b.setStatus(original.getStatus());
        b.setSubtotal(portion.subtotal());
        b.setDiscountAmount(portion.discountAmount());
        b.setPromotionDiscountAmount(portion.promotionDiscountAmount());
        b.setCouponDiscount(portion.couponDiscount());
        b.setParcelCharge(portion.parcelCharge());
        b.setCgstAmount(portion.cgstAmount());
        b.setSgstAmount(portion.sgstAmount());
        b.setTaxAmount(portion.taxAmount());
        b.setRoundOff(portion.roundOff());
        b.setNetAmount(portion.netAmount());
        List<BillItem> items = new ArrayList<>();
        for (PortionItem pi : portion.items()) {
            BillItem from = source.get(pi.billItemId());
            BillItem copy = new BillItem();
            copy.setItemName(pi.itemName());
            copy.setQuantity(pi.quantity());
            copy.setPrice(pi.unitPrice());
            copy.setSubtotal(pi.subtotal());
            copy.setIsTakeaway(pi.takeaway());
            if (from != null) {
                copy.setCustomization(from.getCustomization());
                copy.setModifiersSnapshot(from.getModifiersSnapshot());
                copy.setComboSnapshot(from.getComboSnapshot());
            }
            items.add(copy);
        }
        b.setItems(items);
        return b;
    }

    /**
     * Shares {@code total} between the weights in proportion, in whole multiples of {@code unit}, so the parts add up to the
     * total exactly. The leftover units go to the largest remainders (the earlier bill on a tie). With no weight at all the
     * parts are equal.
     */
    static BigDecimal[] allocate(BigDecimal total, BigDecimal[] weights, BigDecimal unit) {
        int n = weights.length;
        BigDecimal[] w = weights.clone();
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal x : w) sum = sum.add(x);
        if (sum.signum() <= 0) {
            for (int i = 0; i < n; i++) w[i] = BigDecimal.ONE;
            sum = BigDecimal.valueOf(n);
        }
        BigDecimal units = total.divide(unit, 0, RoundingMode.HALF_UP);
        BigDecimal[] floor = new BigDecimal[n];
        BigDecimal[] rest = new BigDecimal[n];
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal[] qr = units.multiply(w[i]).divideAndRemainder(sum);
            floor[i] = qr[0].setScale(0, RoundingMode.DOWN);
            rest[i] = qr[1];
            given = given.add(floor[i]);
        }
        int leftover = units.subtract(given).intValueExact();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort(Comparator.<Integer, BigDecimal>comparing(i -> rest[i]).reversed().thenComparing(i -> i));
        for (int k = 0; k < leftover; k++) floor[order.get(k)] = floor[order.get(k)].add(BigDecimal.ONE);
        BigDecimal[] out = new BigDecimal[n];
        for (int i = 0; i < n; i++) out[i] = floor[i].multiply(unit).setScale(2, RoundingMode.UNNECESSARY);
        return out;
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
