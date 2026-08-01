package dev.forme.operations.auth;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<AccountRow> accounts = jdbcTemplate.query("""
                SELECT username, password_hash, active
                  FROM app_users
                 WHERE username = ?
                """, (rs, rowNum) -> new AccountRow(
                rs.getString("username"), rs.getString("password_hash"), rs.getBoolean("active")), username);
        if (accounts.isEmpty()) {
            throw new UsernameNotFoundException("운영자 계정을 찾을 수 없습니다.");
        }

        AccountRow account = accounts.getFirst();
        String[] roles = jdbcTemplate.query("""
                SELECT r.code
                  FROM roles r
                  JOIN user_roles ur ON ur.role_id = r.id
                  JOIN app_users u ON u.id = ur.user_id
                 WHERE u.username = ?
                 ORDER BY r.code
                """, (rs, rowNum) -> rs.getString("code"), username).toArray(String[]::new);

        return User.withUsername(account.username())
                .password(account.passwordHash())
                .roles(roles)
                .disabled(!account.active())
                .build();
    }

    private record AccountRow(String username, String passwordHash, boolean active) { }
}
