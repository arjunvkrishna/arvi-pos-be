package com.neram.pos.access;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps the Hibernate "outletScope" filter on the current session in line with {@link OutletContext}, so every
 * JPQL/derived query silently returns only the caller's branch (plus cafe-wide rows).
 */
public class OutletFilterSupport {

    public static final String FILTER = "outletScope";
    public static final String PARAM = "outletId";

    private final EntityManagerFactory emf;

    public OutletFilterSupport(EntityManagerFactory emf) {
        this.emf = emf;
        OutletContext.setChangeListener(this::apply);
    }

    /** Applies the context to the entity manager bound to the running transaction, if any. */
    public void apply() {
        Object holder = TransactionSynchronizationManager.getResource(emf);
        if (!(holder instanceof EntityManagerHolder emHolder)) {
            return;
        }
        EntityManager em = emHolder.getEntityManager();
        Session session = em.unwrap(Session.class);
        Long outletId = OutletContext.current().orElse(null);
        if (outletId == null) {
            session.disableFilter(FILTER);
        } else {
            session.enableFilter(FILTER).setParameter(PARAM, outletId);
        }
    }
}
