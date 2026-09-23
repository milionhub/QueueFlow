package com.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * Fail-fast validation of the JWT configuration, both on the key decoding
 * itself and through a real application context. No message may contain the
 * configured secret.
 */
class JwtConfigTest {

    private static final String VALID_SECRET = base64("0123456789abcdef0123456789abcdef"); // 32 bytes

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues("queueflow.security.jwt.issuer=queueflow", "queueflow.security.jwt.ttl=PT1H");

    @Test
    void acceptsBase64KeyOfAtLeast32Bytes() {
        SecretKey key = JwtConfig.hs256Key(VALID_SECRET);

        assertThat(key.getEncoded()).hasSize(32);
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void rejectsMissingOrBlankSecret() {
        assertThatIllegalStateException().isThrownBy(() -> JwtConfig.hs256Key(null))
                .withMessageContaining("JWT_SECRET");
        assertThatIllegalStateException().isThrownBy(() -> JwtConfig.hs256Key("   "))
                .withMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsInvalidBase64WithoutEchoingIt() {
        String notBase64 = "this-is-not-base64!!-but-long-enough-to-matter";

        assertThatIllegalStateException().isThrownBy(() -> JwtConfig.hs256Key(notBase64))
                .withMessage("queueflow.security.jwt.secret is not valid Base64")
                .withNoCause();
    }

    @Test
    void rejectsKeyShorterThan32Bytes() {
        String shortSecret = base64("0123456789abcdef0123456789abcde"); // 31 bytes

        assertThatIllegalStateException().isThrownBy(() -> JwtConfig.hs256Key(shortSecret))
                .withMessageContaining("at least 32 bytes")
                .withMessageNotContaining(shortSecret);
    }

    @Test
    void contextProvidesEncoderAndDecoderForAValidSecret() {
        contextRunner.withPropertyValues("queueflow.security.jwt.secret=" + VALID_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
            // The decoded key is shared internally, never published as a bean.
            assertThat(context).doesNotHaveBean(SecretKey.class);
        });
    }

    @Test
    void contextFailsWithoutSecret() {
        contextRunner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void contextFailsForInvalidOrShortSecretWithoutLeakingIt() {
        String shortSecret = base64("too-short");
        contextRunner.withPropertyValues("queueflow.security.jwt.secret=" + shortSecret).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("at least 32 bytes");
            assertThat(stackTraceOf(context.getStartupFailure())).doesNotContain(shortSecret);
        });

        String notBase64 = "%%%not-base64%%%";
        contextRunner.withPropertyValues("queueflow.security.jwt.secret=" + notBase64).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("not valid Base64");
            assertThat(stackTraceOf(context.getStartupFailure())).doesNotContain(notBase64);
        });
    }

    @Test
    void contextFailsForBlankIssuerOrNonPositiveTtl() {
        contextRunner.withPropertyValues("queueflow.security.jwt.secret=" + VALID_SECRET,
                "queueflow.security.jwt.issuer= ").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("queueflow.security.jwt.secret=" + VALID_SECRET,
                "queueflow.security.jwt.ttl=PT0S").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void propertiesNeverPrintTheSecret() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, "queueflow", Duration.ofHours(1));

        assertThat(properties.toString()).doesNotContain(VALID_SECRET).contains("<redacted>");
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

    private static String stackTraceOf(Throwable failure) {
        StringWriter out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
