/*
 * Copyright 2019 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.server.service;

import com.thoughtworks.go.config.exceptions.ConflictException;
import com.thoughtworks.go.config.exceptions.RecordNotFoundException;
import com.thoughtworks.go.domain.AccessToken;
import com.thoughtworks.go.server.dao.AccessTokenSqlMapDao;
import com.thoughtworks.go.server.dao.DatabaseAccessHelper;
import com.thoughtworks.go.server.exceptions.InvalidAccessTokenException;
import com.thoughtworks.go.server.exceptions.RevokedAccessTokenException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:WEB-INF/applicationContext-global.xml",
        "classpath:WEB-INF/applicationContext-dataLocalAccess.xml",
        "classpath:testPropertyConfigurer.xml"
})
public class AccessTokenServiceIntegrationTest {
    @Autowired
    DatabaseAccessHelper dbHelper;

    @Autowired
    AccessTokenService accessTokenService;

    @Autowired
    AccessTokenSqlMapDao accessTokenSqlMapDao;
    private String authConfigId;

    @Before
    public void setUp() throws Exception {
        dbHelper.onSetUp();
        authConfigId = "auth-config-1";
    }

    @After
    public void tearDown() throws Exception {
        accessTokenSqlMapDao.deleteAll();
        dbHelper.onTearDown();
    }

    @Test
    public void shouldCreateAnAccessToken() throws Exception {
        String tokenDescription = "This is my first token";

        AccessToken.AccessTokenWithDisplayValue createdToken = accessTokenService.create(tokenDescription, "bob", authConfigId);

        AccessToken fetchedToken = accessTokenService.find(createdToken.getId(), "bob");

        assertThat(createdToken.getDescription()).isEqualTo(tokenDescription);
        assertThat(createdToken.getValue()).isNotNull();
        assertThat(createdToken.getDisplayValue()).isNotNull();
        assertThat(createdToken.getCreatedAt()).isNotNull();
        assertThat(createdToken.getLastUsed()).isNull();
        assertThat(createdToken.isRevoked()).isFalse();

        assertThat(fetchedToken.getValue()).isEqualTo(createdToken.getValue());
        assertThat(fetchedToken.getDescription()).isEqualTo(createdToken.getDescription());
        assertThat(fetchedToken.getCreatedAt()).isEqualTo(createdToken.getCreatedAt());
        assertThat(fetchedToken.getLastUsed()).isNull();
        assertThat(fetchedToken.isRevoked()).isEqualTo(createdToken.isRevoked());
    }

    @Test
    public void shouldGetAccessTokenProvidedTokenValue() throws Exception {
        String tokenDescription = "This is my first Token";

        AccessToken.AccessTokenWithDisplayValue createdToken = accessTokenService.create(tokenDescription, "bob", authConfigId);
        String accessTokenInString = createdToken.getDisplayValue();

        AccessToken fetchedToken = accessTokenService.findByAccessToken(accessTokenInString);

        assertThat(fetchedToken).isEqualTo(createdToken);
    }

    @Test
    public void shouldFailToGetAccessTokenWhenProvidedTokenLengthIsNotEqualTo40() {
        InvalidAccessTokenException exception = assertThrows(InvalidAccessTokenException.class, () -> accessTokenService.findByAccessToken("my-access-token"));
        assertThat("Invalid Personal Access Token.").isEqualTo(exception.getMessage());
    }

    @Test
    public void shouldFailToGetAccessTokenWhenProvidedTokenContainsInvalidSaltId() {
        String accessToken = RandomStringUtils.randomAlphanumeric(40);
        InvalidAccessTokenException exception = assertThrows(InvalidAccessTokenException.class, () -> accessTokenService.findByAccessToken(accessToken));
        assertThat("Invalid Personal Access Token.").isEqualTo(exception.getMessage());
    }

    @Test
    public void shouldFailToGetAccessTokenWhenProvidedTokenHashEqualityFails() throws Exception {
        long id = 42;
        String tokenDescription = "This is my first Token";

        AccessToken.AccessTokenWithDisplayValue createdToken = accessTokenService.create(tokenDescription, "bob", authConfigId);
        String accessTokenInString = createdToken.getDisplayValue();
        //replace last 5 characters to make the current token invalid
        String invalidAccessToken = StringUtils.replace(accessTokenInString, accessTokenInString.substring(35), "abcde");

        InvalidAccessTokenException exception = assertThrows(InvalidAccessTokenException.class, () -> accessTokenService.findByAccessToken(invalidAccessToken));
        assertThat("Invalid Personal Access Token.").isEqualTo(exception.getMessage());
    }

    @Test
    public void shouldNotGetAccessTokenProvidedTokenValueWhenTokenIsRevoked() throws Exception {
        long id = 42;
        String tokenDescription = "This is my first Token";

        AccessToken.AccessTokenWithDisplayValue createdToken = accessTokenService.create(tokenDescription, "bob", authConfigId);
        accessTokenService.revokeAccessToken(createdToken.getId(), "BOB", null);
        String accessTokenInString = createdToken.getDisplayValue();

        RevokedAccessTokenException exception = assertThrows(RevokedAccessTokenException.class, () -> accessTokenService.findByAccessToken(accessTokenInString));
        assertThat(exception.getMessage()).startsWith("Invalid Personal Access Token. Access token was revoked at: ");
    }

    @Test
    public void shouldRevokeAnAccessToken() throws Exception {
        String tokenDescription = "This is my first Token";
        AccessToken createdToken = accessTokenService.create(tokenDescription, "BOB", authConfigId);

        assertThat(createdToken.isRevoked()).isFalse();
        assertThat(createdToken.getRevokeCause()).isBlank();

        accessTokenService.revokeAccessToken(createdToken.getId(), "bob", "blah");

        AccessToken tokenAfterRevoking = accessTokenService.find(createdToken.getId(), "bOb");
        assertThat(tokenAfterRevoking.isRevoked()).isTrue();
        assertThat(tokenAfterRevoking.getRevokeCause()).isEqualTo("blah");
    }

    @Test
    public void shouldFailToRevokeAnAlreadyRevokedAccessToken() throws Exception {
        String tokenDescription = "This is my first Token";
        AccessToken createdToken = accessTokenService.create(tokenDescription, "BOB", authConfigId);

        assertThat(createdToken.isRevoked()).isFalse();

        accessTokenService.revokeAccessToken(createdToken.getId(), "bOb", null);

        AccessToken tokenAfterRevoking = accessTokenService.find(createdToken.getId(), "bOb");
        assertThat(tokenAfterRevoking.isRevoked()).isTrue();

        assertThatCode(() -> accessTokenService.revokeAccessToken(createdToken.getId(), "bOb", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Access token has already been revoked!");
    }

    @Test
    public void shouldFailToRevokeNonExistingAccessToken() {
        long id = 42;

        assertThatCode(() -> accessTokenService.revokeAccessToken(id, "bOb", null))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage("Cannot locate access token with id 42");
    }
}
