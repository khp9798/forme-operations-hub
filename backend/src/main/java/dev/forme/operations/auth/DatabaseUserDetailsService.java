package dev.forme.operations.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public DatabaseUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccountRepository.AccountRow account = repository.findAccount(username);
        if (account == null) {
            throw new UsernameNotFoundException("운영자 계정을 찾을 수 없습니다.");
        }

        String[] roles = repository.findRoles(username);

        return User.withUsername(account.username())
                .password(account.passwordHash())
                .roles(roles)
                .disabled(!account.active())
                .build();
    }
}
