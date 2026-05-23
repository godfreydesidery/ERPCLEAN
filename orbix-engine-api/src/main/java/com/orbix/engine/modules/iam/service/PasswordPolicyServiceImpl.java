package com.orbix.engine.modules.iam.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    /** US-IAM-005: minimum length for a user-chosen password. */
    static final int MIN_LENGTH = 10;
    private static final String LEAK_LIST = "security/common-passwords.txt";

    private Set<String> leaked = Set.of();

    @PostConstruct
    void load() {
        Set<String> set = new HashSet<>();
        ClassPathResource res = new ClassPathResource(LEAK_LIST);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim().toLowerCase();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    set.add(t);
                }
            }
        } catch (IOException e) {
            log.warn("Could not load common-password list '{}'; leak check disabled", LEAK_LIST, e);
        }
        this.leaked = Set.copyOf(set);
        log.info("Password policy loaded {} common-password entries", leaked.size());
    }

    @Override
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (leaked.contains(rawPassword.toLowerCase())) {
            throw new IllegalArgumentException(
                "Password is too common — choose a less predictable one");
        }
    }
}
