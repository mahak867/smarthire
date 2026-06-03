// ── SmartHire · src/main/java/com/smarthire/security/UserDetailsServiceImpl.java ──
package com.smarthire.security;

import com.smarthire.domain.entities.User;
import com.smarthire.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userIdOrEmail) throws UsernameNotFoundException {
        User user;
        try {
            UUID id = UUID.fromString(userIdOrEmail);
            user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userIdOrEmail));
        } catch (IllegalArgumentException e) {
            user = userRepository.findByEmailIgnoreCase(userIdOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userIdOrEmail));
        }

        if (!user.isActive()) {
            throw new UsernameNotFoundException("Account is deactivated: " + userIdOrEmail);
        }

        return new org.springframework.security.core.userdetails.User(
            user.getId().toString(),
            user.getPasswordHash(),
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
