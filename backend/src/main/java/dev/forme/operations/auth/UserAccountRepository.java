package dev.forme.operations.auth;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {
    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    AccountRow findAccount(String username) {
        List<AccountRow> rows = jdbc.query("""
                SELECT username, password_hash, active FROM app_users WHERE username=?
                """, (rs, row) -> new AccountRow(rs.getString("username"),
                rs.getString("password_hash"), rs.getBoolean("active")), username);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    String[] findRoles(String username) {
        return jdbc.query("""
                SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id
                  JOIN app_users u ON u.id=ur.user_id WHERE u.username=? ORDER BY r.code
                """, (rs, row) -> rs.getString("code"), username).toArray(String[]::new);
    }

    record AccountRow(String username, String passwordHash, boolean active) { }
}
