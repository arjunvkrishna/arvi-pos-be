package com.neram.pos.service;

import com.neram.pos.dto.CustomerDTO;
import com.neram.pos.dto.LoyaltyLedgerDTO;
import com.neram.pos.entity.Customer;
import com.neram.pos.entity.LoyaltyLedger;
import com.neram.pos.repository.CustomerRepository;
import com.neram.pos.repository.LoyaltyLedgerRepository;
import com.neram.pos.util.BusinessDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final LoyaltyLedgerRepository loyaltyLedgerRepository;
    private final LoyaltySettingsService loyaltySettings;

    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.length() == 10) return "+91" + digits;
        if (digits.startsWith("91") && digits.length() == 12) return "+" + digits;
        return digits;
    }

    public Optional<CustomerDTO> findByPhone(String phone) {
        String norm = normalizePhone(phone);
        return customerRepository.findByPhone(norm).map(this::toDTO);
    }

    public Optional<CustomerDTO> findById(Long id) {
        return customerRepository.findById(id).map(this::toDTO);
    }

    public List<CustomerDTO> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return customerRepository.search(query.trim()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Page<CustomerDTO> listCustomers(Pageable pageable) {
        return customerRepository.findByIsActiveTrueOrderByNameAsc(pageable).map(this::toDTO);
    }

    @Transactional
    public CustomerDTO createCustomer(CustomerDTO dto) {
        String phone = normalizePhone(dto.getPhone());
        if (customerRepository.findByPhone(phone).isPresent()) {
            throw new IllegalArgumentException("Customer with phone " + phone + " already exists.");
        }
        Customer c = Customer.builder()
                .name(dto.getName().trim())
                .phone(phone)
                .email(dto.getEmail() != null ? dto.getEmail().trim() : null)
                .birthday(dto.getBirthday())
                .anniversary(dto.getAnniversary())
                .address(dto.getAddress())
                .allergies(dto.getAllergies())
                .notes(dto.getNotes())
                .marketingConsent(Boolean.TRUE.equals(dto.getMarketingConsent()))
                .pointsBalance(0)
                .isActive(true)
                .build();
        return toDTO(customerRepository.save(c));
    }

    @Transactional
    public CustomerDTO updateCustomer(Long id, CustomerDTO dto) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        if (dto.getName() != null) c.setName(dto.getName().trim());
        if (dto.getEmail() != null) c.setEmail(dto.getEmail().trim());
        if (dto.getBirthday() != null) c.setBirthday(dto.getBirthday());
        if (dto.getAnniversary() != null) c.setAnniversary(dto.getAnniversary());
        if (dto.getAddress() != null) c.setAddress(dto.getAddress());
        if (dto.getAllergies() != null) c.setAllergies(dto.getAllergies());
        if (dto.getNotes() != null) c.setNotes(dto.getNotes());
        if (dto.getMarketingConsent() != null) c.setMarketingConsent(dto.getMarketingConsent());
        return toDTO(customerRepository.save(c));
    }

    @Transactional
    public void earnPoints(Long customerId, Long billId, BigDecimal monetarySpend) {
        earnPoints(customerId, billId, monetarySpend, BusinessDate.now());
    }

    /**
     * A purchase by a customer who gave a phone number: ₹100 of paid spend earns 1 point. Whether or not it earns
     * anything, the purchase starts the validity period again, so points only lapse after a long quiet spell.
     * Points that had already lapsed are gone first and never come back with the new purchase.
     */
    @Transactional
    public void earnPoints(Long customerId, Long billId, BigDecimal monetarySpend, LocalDate today) {
        if (customerId == null) return;
        Customer c = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        expireIfDue(c, today);

        int pts = monetarySpend == null || monetarySpend.signum() <= 0 ? 0
                : monetarySpend.divide(BigDecimal.valueOf(LoyaltySettingsService.RUPEES_PER_POINT_EARNED), 0, java.math.RoundingMode.FLOOR).intValue();
        if (pts > 0) {
            int newBal = c.getPointsBalance() + pts;
            c.setPointsBalance(newBal);
            loyaltyLedgerRepository.save(LoyaltyLedger.builder()
                    .customerId(customerId)
                    .billId(billId)
                    .type("EARN")
                    .points(pts)
                    .balanceAfter(newBal)
                    .note("Earned from bill #" + billId)
                    .build());
        }
        settleExpiry(c, today, true);
        customerRepository.save(c);
    }

    @Transactional
    public void redeemPoints(Long customerId, Long billId, int pointsToRedeem) {
        redeemPoints(customerId, billId, pointsToRedeem, BusinessDate.now());
    }

    /** Points pay ₹1 each, but only from a balance of at least the minimum and in lots of at least the minimum. */
    @Transactional
    public void redeemPoints(Long customerId, Long billId, int pointsToRedeem, LocalDate today) {
        if (customerId == null || pointsToRedeem <= 0) return;
        Customer c = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        LoyaltySettingsService.Rules rules = loyaltySettings.rules();
        if (expireIfDue(c, today)) {
            throw new IllegalArgumentException("This customer's loyalty points have expired (no purchase within " + rules.expiryDays() + " days).");
        }
        if (c.getPointsBalance() < rules.minRedeemPoints()) {
            throw new IllegalArgumentException("A customer needs at least " + rules.minRedeemPoints() + " loyalty points to redeem. Balance: " + c.getPointsBalance());
        }
        if (pointsToRedeem < rules.minRedeemPoints()) {
            throw new IllegalArgumentException("The least that can be redeemed is " + rules.minRedeemPoints() + " loyalty points. Requested: " + pointsToRedeem);
        }
        if (c.getPointsBalance() < pointsToRedeem) {
            throw new IllegalArgumentException("Insufficient loyalty points. Balance: " + c.getPointsBalance() + ", Requested: " + pointsToRedeem);
        }

        int newBal = c.getPointsBalance() - pointsToRedeem;
        c.setPointsBalance(newBal);
        if (newBal == 0) c.setPointsExpireAt(null);
        customerRepository.save(c);

        loyaltyLedgerRepository.save(LoyaltyLedger.builder()
                .customerId(customerId)
                .billId(billId)
                .type("REDEEM")
                .points(-pointsToRedeem)
                .balanceAfter(newBal)
                .note("Redeemed on bill #" + billId)
                .build());
    }

    /** Ids of customers whose points have lapsed (the hourly job expires each one in its own transaction). */
    public List<Long> idsDueForExpiry(LocalDate today) {
        return customerRepository.findIdsDueForExpiry(today);
    }

    /** Expires one customer's lapsed points; returns how many points were lost (0 when nothing was due). */
    @Transactional
    public int expireOne(Long customerId, LocalDate today) {
        Customer c = customerRepository.findByIdForUpdate(customerId).orElse(null);
        if (c == null) return 0;
        int before = c.getPointsBalance();
        return expireIfDue(c, today) ? before : 0;
    }

    /** Lapses the whole balance when the customer has not bought by the last valid day. */
    private boolean expireIfDue(Customer c, LocalDate today) {
        LocalDate lastValid = c.getPointsExpireAt();
        if (c.getPointsBalance() <= 0 || lastValid == null || !today.isAfter(lastValid)) return false;
        int lost = c.getPointsBalance();
        c.setPointsBalance(0);
        c.setPointsExpireAt(null);
        customerRepository.save(c);
        loyaltyLedgerRepository.save(LoyaltyLedger.builder()
                .customerId(c.getId())
                .type("EXPIRE")
                .points(-lost)
                .balanceAfter(0)
                .note("Points expired: valid through " + lastValid + " and no purchase since")
                .build());
        log.info("Customer {} lost {} loyalty point(s): valid through {}", c.getId(), lost, lastValid);
        return true;
    }

    /** No points means no expiry date; points with no date (older rows) get one; a purchase moves the date forward. */
    private void settleExpiry(Customer c, LocalDate today, boolean purchase) {
        if (c.getPointsBalance() <= 0) {
            c.setPointsExpireAt(null);
        } else if (purchase || c.getPointsExpireAt() == null) {
            c.setPointsExpireAt(today.plusDays(loyaltySettings.rules().expiryDays()));
        }
    }

    @Transactional
    public void reversePointsForBill(Long billId) {
        reversePointsForBill(billId, BusinessDate.now());
    }

    @Transactional
    public void reversePointsForBill(Long billId, LocalDate today) {
        if (billId == null) return;
        List<LoyaltyLedger> entries = loyaltyLedgerRepository.findByBillId(billId);
        boolean alreadyReversed = entries.stream().anyMatch(e -> "REFUND".equals(e.getType()));
        if (alreadyReversed) {
            log.info("Loyalty points reversal already processed for bill {}", billId);
            return;
        }
        for (LoyaltyLedger entry : entries) {
            Customer c = customerRepository.findByIdForUpdate(entry.getCustomerId()).orElse(null);
            if (c == null) continue;

            if ("EARN".equals(entry.getType())) {
                // Reverse earned points: floor at 0
                int pointsToReverse = entry.getPoints();
                int currentBal = c.getPointsBalance();
                int newBal = Math.max(0, currentBal - pointsToReverse);
                c.setPointsBalance(newBal);
                settleExpiry(c, today, false);
                customerRepository.save(c);

                loyaltyLedgerRepository.save(LoyaltyLedger.builder()
                        .customerId(c.getId())
                        .billId(billId)
                        .type("REFUND")
                        .points(-pointsToReverse)
                        .balanceAfter(newBal)
                        .note("Reversed earned points for voided/refunded bill #" + billId + (currentBal < pointsToReverse ? " (deficited)" : ""))
                        .build());
            } else if ("REDEEM".equals(entry.getType())) {
                // Credit back redeemed points
                int pointsToRestore = Math.abs(entry.getPoints());
                int newBal = c.getPointsBalance() + pointsToRestore;
                c.setPointsBalance(newBal);
                settleExpiry(c, today, false);
                customerRepository.save(c);

                loyaltyLedgerRepository.save(LoyaltyLedger.builder()
                        .customerId(c.getId())
                        .billId(billId)
                        .type("REFUND")
                        .points(pointsToRestore)
                        .balanceAfter(newBal)
                        .note("Restored redeemed points for voided/refunded bill #" + billId)
                        .build());
            }
        }
    }

    @Transactional
    public boolean unsubscribe(String token) {
        Optional<Customer> opt = customerRepository.findByUnsubscribeToken(token);
        if (opt.isPresent()) {
            Customer c = opt.get();
            c.setMarketingConsent(false);
            customerRepository.save(c);
            return true;
        }
        return false;
    }

    public List<LoyaltyLedgerDTO> getLoyaltyHistory(Long customerId) {
        return loyaltyLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(l -> LoyaltyLedgerDTO.builder()
                        .id(l.getId())
                        .customerId(l.getCustomerId())
                        .billId(l.getBillId())
                        .type(l.getType())
                        .points(l.getPoints())
                        .balanceAfter(l.getBalanceAfter())
                        .note(l.getNote())
                        .createdAt(l.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public CustomerDTO toDTO(Customer c) {
        return CustomerDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .birthday(c.getBirthday())
                .anniversary(c.getAnniversary())
                .address(c.getAddress())
                .allergies(c.getAllergies())
                .notes(c.getNotes())
                .marketingConsent(c.getMarketingConsent())
                .pointsBalance(c.getPointsBalance())
                .pointsExpireAt(c.getPointsExpireAt())
                .isActive(c.getIsActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
