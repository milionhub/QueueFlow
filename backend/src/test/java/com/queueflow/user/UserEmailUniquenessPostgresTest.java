package com.queueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.queueflow.workspace.Workspace;
import com.queueflow.workspace.WorkspaceRepository;

/**
 * V5 against real PostgreSQL: email uniqueness holds regardless of letter
 * case even when application normalization is bypassed entirely. Runs in
 * @DataJpaTest's rolled-back transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserEmailUniquenessPostgresTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = workspaceRepository.saveAndFlush(new Workspace("V5 Workspace"));
        userRepository.saveAndFlush(new User("Juan", "juan@example.com", "hash", UserRole.MEMBER, workspace));
    }

    /** One variant per test: a rejected insert aborts the (rolled-back) test transaction. */
    @ParameterizedTest
    @ValueSource(strings = {"JUAN@example.com", "Juan@Example.Com", "juan@EXAMPLE.COM"})
    void caseVariantsOfAnExistingEmailAreRejectedThroughJpa(String variant) {
        Throwable thrown = catchThrowable(() -> userRepository.saveAndFlush(
                new User("Impostor", variant, "hash", UserRole.MEMBER, workspace)));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(thrown)).isEqualTo("23505"); // unique_violation -> API 409
    }

    @Test
    void caseVariantsAreRejectedThroughPlainSql() {
        Throwable thrown = catchThrowable(() -> jdbcTemplate.update(
                "INSERT INTO users (name, email, password_hash, role, workspace_id) VALUES (?, ?, ?, ?, ?)",
                "Impostor", "JUAN@EXAMPLE.COM", "hash", "MEMBER", workspace.getId()));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(thrown)).isEqualTo("23505");
    }

    @Test
    void genuinelyDifferentEmailsAreStillAccepted() {
        userRepository.saveAndFlush(new User("Juana", "juana@example.com", "hash", UserRole.MEMBER, workspace));

        assertThat(userRepository.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void finalSchemaHasBothTheExactAndTheCaseInsensitiveUniqueness() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'users' AND indexdef LIKE 'CREATE UNIQUE%'"
                        + " ORDER BY indexname", String.class);

        assertThat(indexes).anySatisfy(definition -> assertThat(definition)
                .contains("uq_users_email_lower").contains("lower((email)::text)"));
        assertThat(indexes).anySatisfy(definition -> assertThat(definition)
                .contains("uq_users_email ").contains("(email)"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname = 'uq_users_email'",
                Integer.class)).isEqualTo(1);
    }

    private static String sqlState(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
