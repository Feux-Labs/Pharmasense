package com.pharmasense.admin.service;

import com.pharmasense.admin.config.AdminProperties;
import com.pharmasense.identity.repository.UserAccountRepository;
import com.pharmasense.identity.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * There is no self-service signup for {@code SUPER_ADMIN} - by design, the
 * only way to become a platform admin is to already be one, or to be
 * created here. Set {@code SUPER_ADMIN_BOOTSTRAP_EMAIL} once, start the
 * app, then sign in with the normal email + OTP flow; you can leave the
 * variable set (it no-ops once that account exists) or unset it afterwards.
 */
@Component
public class SuperAdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

    private final UserAccountRepository userAccountRepository;
    private final UserAccountService userAccountService;
    private final AdminProperties adminProperties;

    public SuperAdminBootstrapRunner(
            UserAccountRepository userAccountRepository, UserAccountService userAccountService, AdminProperties adminProperties) {
        this.userAccountRepository = userAccountRepository;
        this.userAccountService = userAccountService;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(String... args) {
        String email = adminProperties.bootstrapEmail();
        if (!StringUtils.hasText(email)) {
            return;
        }
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        userAccountService.createSuperAdminAccount(email, adminProperties.bootstrapFullName());
        log.info("Bootstrapped super-admin account for {} - sign in with the normal email + OTP flow", email);
    }
}
