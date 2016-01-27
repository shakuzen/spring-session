/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.session;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

public class MapSessionRepositoryTests {
	MapSessionRepository repository;

	MapSession session;

	@Before
	public void setup() {
		repository = new MapSessionRepository();
		session = new MapSession();
	}

	@Test
	public void getSessionExpired() {
		session.setMaxInactiveIntervalInSeconds(1);
		session.setLastAccessedTime(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
		repository.save(session);

		assertThat(repository.getSession(session.getId())).isNull();
	}

	@Test
	public void createSessionDefaultExpiration() {
		ExpiringSession session = repository.createSession();

		assertThat(session).isInstanceOf(MapSession.class);
		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(new MapSession().getMaxInactiveIntervalInSeconds());
	}

	@Test
	public void createSessionCustomDefaultExpiration() {
		final int expectedMaxInterval = new MapSession().getMaxInactiveIntervalInSeconds() + 10;
		repository.setDefaultMaxInactiveInterval(expectedMaxInterval);

		ExpiringSession session = repository.createSession();

		assertThat(session.getMaxInactiveIntervalInSeconds()).isEqualTo(expectedMaxInterval);
	}
}