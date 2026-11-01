package com.srikumar.multitenantcrm.testsupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Like {@link AbstractIsolationTest} but with a real HTTP server, for tests that exercise the
 * JWT filter + tenant resolution end to end rather than driving repositories directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractHttpIsolationTest extends AbstractIsolationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
