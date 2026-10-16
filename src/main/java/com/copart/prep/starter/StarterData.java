package com.copart.prep.starter;

import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import com.copart.prep.signin.AccountRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class StarterData implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(StarterData.class);

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public StarterData(AccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAccount("ada@example.com", AccountRole.CUSTOMER);
        seedAccount("grace@example.com", AccountRole.CUSTOMER);
    }

    private void seedAccount(String email, AccountRole role) {
        if (accounts.findByEmail(email).isPresent()) {
            return;
        }
        accounts.save(new Account(email, passwordEncoder.encode("password"), role));
        logger.info("Seeded {} {}", role.name().toLowerCase(), email);
    }
}
