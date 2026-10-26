package com.neram.pos.access;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

@Configuration
public class OutletScopeConfig {

    @Bean
    public OutletFilterSupport outletFilterSupport(EntityManagerFactory emf) {
        return new OutletFilterSupport(emf);
    }

    /**
     * Repository query methods run without a transaction, on the request-wide entity manager, so the filter must also
     * be switched on as soon as that entity manager is opened (after Spring's open-in-view interceptor).
     */
    @Bean
    public org.springframework.web.servlet.config.annotation.WebMvcConfigurer outletFilterWebConfigurer(OutletFilterSupport filterSupport) {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            @Override
            public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                registry.addWebRequestInterceptor(new org.springframework.web.context.request.WebRequestInterceptor() {
                    @Override
                    public void preHandle(org.springframework.web.context.request.WebRequest request) {
                        filterSupport.apply();
                    }

                    @Override
                    public void postHandle(org.springframework.web.context.request.WebRequest request,
                                           org.springframework.ui.ModelMap model) {
                    }

                    @Override
                    public void afterCompletion(org.springframework.web.context.request.WebRequest request, Exception ex) {
                    }
                }).order(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
            }
        };
    }

    /** Applies the branch filter whenever a transaction starts on a session. */
    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory emf, OutletFilterSupport filterSupport) {
        return new JpaTransactionManager(emf) {
            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                super.doBegin(transaction, definition);
                filterSupport.apply();
            }
        };
    }
}
