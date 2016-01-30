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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.Region;

/**
 * The GemFireHttpSessionConfigurationXmlTests class is a test suite of test cases testing the configuration of
 * Spring Session backed by GemFire using XML configuration meta-data.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @since 1.1.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class GemFireHttpSessionConfigurationXmlTests extends AbstractGemFireIntegrationTests {

	@Autowired
	private Cache gemfireCache;

	@Test
	public void gemfireCacheConfigurationIsValid() {
		assertThat(gemfireCache).isNotNull();

		Region<Object, ExpiringSession> example = gemfireCache.getRegion("Example");

		assertRegion(example, "Example", DataPolicy.NORMAL);
		assertEntryIdleTimeout(example, ExpirationAction.INVALIDATE, 3600);
	}

}
