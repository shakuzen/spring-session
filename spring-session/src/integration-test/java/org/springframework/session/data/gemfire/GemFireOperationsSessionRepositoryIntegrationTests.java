/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.session.data.gemfire.GemFireOperationsSessionRepository.GemFireSession;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.Query;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.pdx.PdxReader;
import com.gemstone.gemfire.pdx.PdxSerializable;
import com.gemstone.gemfire.pdx.PdxWriter;

/**
 * The GemFireOperationsSessionRepositoryIntegrationTests class is a test suite of test cases testing
 * the findByPrincipalName query method on the GemFireOpeationsSessionRepository class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @since 1.1.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class GemFireOperationsSessionRepositoryIntegrationTests extends AbstractGemFireIntegrationTests {
	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 300;

	private static final String GEMFIRE_LOG_LEVEL = "warning";
	private static final String SPRING_SESSION_GEMFIRE_REGION_NAME = "TestPartitionedSessions";

	SecurityContext context;

	SecurityContext changedContext;

	@Before
	public void setup() {
		context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken("username-"+UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));

		changedContext = SecurityContextHolder.createEmptyContext();
		changedContext.setAuthentication(new UsernamePasswordAuthenticationToken("changedContext-"+UUID.randomUUID(), "na", AuthorityUtils.createAuthorityList("ROLE_USER")));

		assertThat(gemfireCache).isNotNull();
		assertThat(gemfireSessionRepository).isNotNull();
		assertThat(gemfireSessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(
			MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Region<Object, ExpiringSession> sessionRegion = gemfireCache.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertRegion(sessionRegion, SPRING_SESSION_GEMFIRE_REGION_NAME, DataPolicy.PARTITION);
		assertEntryIdleTimeout(sessionRegion, ExpirationAction.INVALIDATE, MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	protected Map<String, ExpiringSession> doFindByPrincipalName(String principalName) {
		return gemfireSessionRepository.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
	}

	@SuppressWarnings("unchecked")
	protected Map<String, ExpiringSession> doFindByPrincipalName(String regionName, String principalName) {
		try {
			Region<String, ExpiringSession> region = gemfireCache.getRegion(regionName);

			assertThat(region).isNotNull();

			QueryService queryService = region.getRegionService().getQueryService();

			String queryString = String.format("SELECT s FROM %1$s s WHERE s.principalName = $1", region.getFullPath());

			Query query = queryService.newQuery(queryString);

			SelectResults<ExpiringSession> results = (SelectResults<ExpiringSession>) query.execute(
				new Object[] { principalName });

			Map<String, ExpiringSession> sessions = new HashMap<String, ExpiringSession>(results.size());

			for (ExpiringSession session : results.asList()) {
				sessions.put(session.getId(), session);
			}

			return sessions;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected boolean enableQueryDebugging() {
		return true;
	}

	@Test
	public void findSessionsByPrincipalName() {
		ExpiringSession sessionOne = save(touch(createSession("robWinch")));
		ExpiringSession sessionTwo = save(touch(createSession("johnBlum")));
		ExpiringSession sessionThree = save(touch(createSession("robWinch")));
		ExpiringSession sessionFour = save(touch(createSession("johnBlum")));
		ExpiringSession sessionFive = save(touch(createSession("robWinch")));

		assertThat(get(sessionOne.getId())).isEqualTo(sessionOne);
		assertThat(get(sessionTwo.getId())).isEqualTo(sessionTwo);
		assertThat(get(sessionThree.getId())).isEqualTo(sessionThree);
		assertThat(get(sessionFour.getId())).isEqualTo(sessionFour);
		assertThat(get(sessionFive.getId())).isEqualTo(sessionFive);

		Map<String, ExpiringSession> johnBlumSessions = doFindByPrincipalName("johnBlum");

		assertThat(johnBlumSessions).isNotNull();
		assertThat(johnBlumSessions.size()).isEqualTo(2);
		assertThat(johnBlumSessions.containsKey(sessionOne.getId())).isFalse();
		assertThat(johnBlumSessions.containsKey(sessionThree.getId())).isFalse();
		assertThat(johnBlumSessions.containsKey(sessionFive.getId())).isFalse();
		assertThat(johnBlumSessions.get(sessionTwo.getId())).isEqualTo(sessionTwo);
		assertThat(johnBlumSessions.get(sessionFour.getId())).isEqualTo(sessionFour);

		Map<String, ExpiringSession> robWinchSessions = doFindByPrincipalName("robWinch");

		assertThat(robWinchSessions).isNotNull();
		assertThat(robWinchSessions.size()).isEqualTo(3);
		assertThat(robWinchSessions.containsKey(sessionTwo.getId())).isFalse();
		assertThat(robWinchSessions.containsKey(sessionFour.getId())).isFalse();
		assertThat(robWinchSessions.get(sessionOne.getId())).isEqualTo(sessionOne);
		assertThat(robWinchSessions.get(sessionThree.getId())).isEqualTo(sessionThree);
		assertThat(robWinchSessions.get(sessionFive.getId())).isEqualTo(sessionFive);
	}

	@Test
	public void findSessionsBySecurityPrincipalName() {
		ExpiringSession toSave = this.gemfireSessionRepository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);

		save(toSave);

		Map<String, ExpiringSession> findByPrincipalName = doFindByPrincipalName(getSecurityName());
		assertThat(findByPrincipalName).hasSize(1);
		assertThat(findByPrincipalName.keySet()).containsOnly(toSave.getId());
	}

	@Test
	public void findSessionsByChangedSecurityPrincipalName() {
		ExpiringSession toSave = this.gemfireSessionRepository.createSession();
		toSave.setAttribute(SPRING_SECURITY_CONTEXT, context);
		save(toSave);

		toSave.setAttribute(SPRING_SECURITY_CONTEXT, changedContext);
		save(toSave);

		Map<String, ExpiringSession> findByPrincipalName = doFindByPrincipalName(getSecurityName());
		assertThat(findByPrincipalName).isEmpty();

		findByPrincipalName = doFindByPrincipalName(getChangedSecurityName());
		assertThat(findByPrincipalName).hasSize(1);
	}

	@Test
	public void findsNoSessionsByNonExistingPrincipal() {
		Map<String, ExpiringSession> nonExistingPrincipalSessions = doFindByPrincipalName("nonExistingPrincipalName");

		assertThat(nonExistingPrincipalSessions).isNotNull();
		assertThat(nonExistingPrincipalSessions.isEmpty()).isTrue();
	}

	@Test
	public void doesNotFindAfterPrincipalRemoved() {
		String username = "doesNotFindAfterPrincipalRemoved";
		ExpiringSession session = save(touch(createSession(username)));
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, null);
		save(session);

		Map<String, ExpiringSession> nonExistingPrincipalSessions = doFindByPrincipalName(username);

		assertThat(nonExistingPrincipalSessions).isNotNull();
		assertThat(nonExistingPrincipalSessions.isEmpty()).isTrue();
	}

	@Test
	public void saveAndReadSessionWithAttributes() {
		ExpiringSession expectedSession = gemfireSessionRepository.createSession();

		assertThat(expectedSession).isInstanceOf(GemFireSession.class);

		((GemFireSession) expectedSession).setPrincipalName("jblum");

		List<String> expectedAttributeNames = Arrays.asList(
			"booleanAttribute", "numericAttribute", "stringAttribute", "personAttribute");

		Person jonDoe = new Person("Jon", "Doe");

		expectedSession.setAttribute(expectedAttributeNames.get(0), true);
		expectedSession.setAttribute(expectedAttributeNames.get(1), Math.PI);
		expectedSession.setAttribute(expectedAttributeNames.get(2), "test");
		expectedSession.setAttribute(expectedAttributeNames.get(3), jonDoe);

		gemfireSessionRepository.save(touch(expectedSession));

		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession).isEqualTo(expectedSession);
		assertThat(savedSession).isInstanceOf(GemFireSession.class);
		assertThat(((GemFireSession) savedSession).getPrincipalName()).isEqualTo("jblum");

		assertThat(savedSession.getAttributeNames().containsAll(expectedAttributeNames)).as(
			String.format("Expected (%1$s); but was (%2$s)", expectedAttributeNames,savedSession.getAttributeNames()))
				.isTrue();

		assertThat(Boolean.valueOf(String.valueOf(savedSession.getAttribute(expectedAttributeNames.get(0))))).isTrue();
		assertThat(Double.valueOf(String.valueOf(savedSession.getAttribute(expectedAttributeNames.get(1)))))
			.isEqualTo(Math.PI);
		assertThat(String.valueOf(savedSession.getAttribute(expectedAttributeNames.get(2)))).isEqualTo("test");
		assertThat(savedSession.getAttribute(expectedAttributeNames.get(3))).isEqualTo(jonDoe);
	}

	private String getSecurityName() {
		return context.getAuthentication().getName();
	}

	private String getChangedSecurityName() {
		return changedContext.getAuthentication().getName();
	}

	@EnableGemFireHttpSession(regionName = SPRING_SESSION_GEMFIRE_REGION_NAME,
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionGemFireConfiguration {

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name", GemFireOperationsSessionRepositoryIntegrationTests.class.getName());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);

			return gemfireProperties;
		}

		@Bean
		CacheFactoryBean gemfireCache() {
			CacheFactoryBean gemfireCache = new CacheFactoryBean();

			gemfireCache.setLazyInitialize(false);
			gemfireCache.setProperties(gemfireProperties());
			gemfireCache.setUseBeanFactoryLocator(false);

			return gemfireCache;
		}
	}

	public static class Person implements PdxSerializable {

		private String firstName;
		private String lastName;

		public Person() {
		}

		public Person(String firstName, String lastName) {
			this.firstName = validate(firstName);
			this.lastName = validate(lastName);
		}

		private String validate(String value) {
			Assert.hasText(value, String.format("The String value (%1$s) must be specified!", value));
			return value;
		}

		public String getFirstName() {
			return firstName;
		}

		public String getLastName() {
			return lastName;
		}

		public String getName() {
			return String.format("%1$s %2$s", getFirstName(), getLastName());
		}

		public void toData(PdxWriter pdxWriter) {
			pdxWriter.writeString("firstName", getFirstName());
			pdxWriter.writeString("lastName", getLastName());
		}

		public void fromData(final PdxReader pdxReader) {
			this.firstName = pdxReader.readString("firstName");
			this.lastName = pdxReader.readString("lastName");
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}

			if (!(obj instanceof Person)) {
				return false;
			}

			Person that = (Person) obj;

			return ObjectUtils.nullSafeEquals(this.getFirstName(), that.getFirstName())
				&& ObjectUtils.nullSafeEquals(this.getLastName(), that.getLastName());
		}

		@Override
		public int hashCode() {
			int hashValue = 17;
			hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(getFirstName());
			hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(getLastName());
			return hashValue;
		}

		@Override
		public String toString() {
			return getName();
		}
	}

}
