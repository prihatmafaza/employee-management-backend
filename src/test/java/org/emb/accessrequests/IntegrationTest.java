package org.emb.accessrequests;

import org.emb.accessrequests.auth.service.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for tests that run the whole app on a random port against a real
 * PostgreSQL. Requests and approvals are wiped before each test; the seeded
 * users and access types stay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final long VPN = 1;
    protected static final long GITHUB = 2;
    protected static final long FIGMA = 3;
    protected static final long JIRA = 4;

    @LocalServerPort
    int port;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    LoginRateLimiter rateLimiter;

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE approvals, access_requests RESTART IDENTITY");
        rateLimiter.clear();
    }

    /** A client with no token. */
    protected ApiClient anonymous() {
        return new ApiClient("http://localhost:" + port, jsonMapper);
    }

    /** A client logged in as the given seed user. */
    protected ApiClient as(String username) {
        return anonymous().login(username);
    }
}
