package com.oshmarket.config;

import com.oshmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApplicationConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner initAdminPassword() {
        return args -> {
            userRepository.findByInnAndDeletedFalse("0000000000").ifPresent(admin -> {
                if (admin.getPasswordHash().contains("PLACEHOLDER")) {
                    admin.setPasswordHash(passwordEncoder.encode("Admin@123456"));
                    userRepository.save(admin);
                    log.info("Admin default password initialized. INN: 0000000000, Password: Admin@123456");
                }
            });
        };
    }
}
