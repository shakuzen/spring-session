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

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.IndexFactoryBean;
import org.springframework.data.gemfire.IndexType;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.StringUtils;

import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.ExpirationAttributes;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * The GemFireHttpSessionConfiguration class is a Spring @Configuration class used to configure and initialize
 * Pivotal GemFire (or Apache Geode) as a clustered, replicated HttpSession provider implementation in Spring Session.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.BeanClassLoaderAware
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.ImportAware
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.data.gemfire.IndexFactoryBean
 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean
 * @see com.gemstone.gemfire.cache.ExpirationAttributes
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.RegionAttributes
 * @since 1.1.0
 */
@Configuration
public class GemFireHttpSessionConfiguration extends SpringHttpSessionConfiguration
		implements BeanClassLoaderAware, ImportAware {

	public static final int DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS = (int) TimeUnit.MINUTES.toSeconds(30);

	protected static final Class<Object> SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT = Object.class;
	protected static final Class<GemFireSession> SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT = GemFireSession.class;

	public static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT = ClientRegionShortcut.PROXY;

	public static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT = RegionShortcut.PARTITION;

	public static final String DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME = "ClusteredSpringSessions";

	private int maxInactiveIntervalInSeconds = DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;

	private ClassLoader beanClassLoader;

	private ClientRegionShortcut clientRegionShortcut = DEFAULT_CLIENT_REGION_SHORTCUT;

	private RegionShortcut serverRegionShortcut = DEFAULT_SERVER_REGION_SHORTCUT;

	private String springSessionGemFireRegionName = DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME;

	/**
	 * Sets a reference to the {@link ClassLoader} used to load bean definition class types in a Spring context.
	 *
	 * @param beanClassLoader the ClassLoader used by the Spring container to load bean class types.
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(ClassLoader)
	 * @see java.lang.ClassLoader
	 */
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	/**
	 * Gets a reference to the {@link ClassLoader} used to load bean definition class types in a Spring context.
	 *
	 * @return the ClassLoader used by the Spring container to load bean class types.
	 * @see java.lang.ClassLoader
	 */
	protected ClassLoader getBeanClassLoader() {
		return beanClassLoader;
	}

	/**
	 * Sets the {@link ClientRegionShortcut} used to configure the GemFire ClientCache Region
	 * that will store Spring Sessions.
	 *
	 * @param shortcut the ClientRegionShortcut used to configure the GemFire ClientCache Region.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	public void setClientRegionShortcut(ClientRegionShortcut shortcut) {
		this.clientRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link ClientRegionShortcut} used to configure the GemFire ClientCache Region
	 * that will store Spring Sessions. Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return the ClientRegionShortcut used to configure the GemFire ClientCache Region.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	protected ClientRegionShortcut getClientRegionShortcut() {
		return (clientRegionShortcut != null ? clientRegionShortcut : DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
	 * Sets the maximum interval in seconds in which a Session can remain inactive before it is considered expired.
	 *
	 * @param maxInactiveIntervalInSeconds an integer value specifying the maximum interval in seconds that a Session
	 * can remain inactive before it is considered expired.
	 */
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	/**
	 * Gets the maximum interval in seconds in which a Session can remain inactive before it is considered expired.
	 *
	 * @return an integer value specifying the maximum interval in seconds that a Session can remain inactive
	 * before it is considered expired.
	 */
	protected int getMaxInactiveIntervalInSeconds() {
		return maxInactiveIntervalInSeconds;
	}

	/**
	 * Sets the {@link RegionShortcut} used to configure the GemFire Cache Region that will store Spring Sessions.
	 *
	 * @param shortcut the RegionShortcut used to configure the GemFire Cache Region.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	public void setServerRegionShortcut(RegionShortcut shortcut) {
		serverRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link RegionShortcut} used to configure the GemFire Cache Region that will store Spring Sessions.
	 * Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return the RegionShortcut used to configure the GemFire Cache Region.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	protected RegionShortcut getServerRegionShortcut() {
		return (serverRegionShortcut != null ? serverRegionShortcut : DEFAULT_SERVER_REGION_SHORTCUT);
	}

	/**
	 * Sets the name of the Gemfire (Client)Cache Region used to store Sessions.
	 *
	 * @param springSessionGemFireRegionName a String specifying the name of the GemFire (Client)Cache Region
	 * used to store the Session.
	 */
	public void setSpringSessionGemFireRegionName(String springSessionGemFireRegionName) {
		this.springSessionGemFireRegionName = springSessionGemFireRegionName;
	}

	/**
	 * Gets the name of the Gemfire (Client)Cache Region used to store Sessions. Defaults to 'ClusteredSpringSessions'.
	 *
	 * @return a String specifying the name of the GemFire (Client)Cache Region
	 * used to store the Session.
	 * @see com.gemstone.gemfire.cache.Region#getName()
	 */
	protected String getSpringSessionGemFireRegionName() {
		return (StringUtils.hasText(springSessionGemFireRegionName) ? springSessionGemFireRegionName
			: DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	/**
	 * Callback with the {@link AnnotationMetadata} of the class containing @Import annotation that imported
	 * this @Configuration class.
	 *
	 * @param importMetadata the AnnotationMetadata of the class importing this @Configuration class.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
	 * @see org.springframework.core.type.AnnotationMetadata
	 */
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		AnnotationAttributes enableGemFireHttpSessionAnnotationAttributes = AnnotationAttributes.fromMap(
			importMetadata.getAnnotationAttributes(EnableGemFireHttpSession.class.getName()));

		setClientRegionShortcut(ClientRegionShortcut.class.cast(enableGemFireHttpSessionAnnotationAttributes.getEnum(
			"clientRegionShortcut")));

		setMaxInactiveIntervalInSeconds(enableGemFireHttpSessionAnnotationAttributes.getNumber(
			"maxInactiveIntervalInSeconds").intValue());

		setServerRegionShortcut(RegionShortcut.class.cast(enableGemFireHttpSessionAnnotationAttributes.getEnum(
			"serverRegionShortcut")));

		setSpringSessionGemFireRegionName(enableGemFireHttpSessionAnnotationAttributes.getString("regionName"));
	}

	/**
	 * Defines the Spring SessionRepository bean used to interact with GemFire as a Spring Session provider.
	 *
	 * @param gemfireOperations an instance of {@link GemfireOperations} used to manage Spring Sessions in GemFire.
	 * @return a GemFireOperationsSessionRepository for managing (clustering/replicating) Sessions using GemFire.
	 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	@Bean
	public GemFireOperationsSessionRepository sessionRepository(@Qualifier("sessionRegionTemplate")
			GemfireOperations gemfireOperations) {

		GemFireOperationsSessionRepository sessionRepository = new GemFireOperationsSessionRepository(gemfireOperations);

		sessionRepository.setMaxInactiveIntervalInSeconds(getMaxInactiveIntervalInSeconds());

		return sessionRepository;
	}

	/**
	 * Defines a Spring GemfireTemplate bean used to interact with GemFire's (Client)Cache {@link Region}
	 * storing Sessions.
	 *
	 * @param gemFireCache reference to the single GemFire cache instance used by the {@link GemfireTemplate}
	 * to perform GemFire cache data access operations.
	 * @return a {@link GemfireTemplate} used to interact with GemFire's (Client)Cache {@link Region} storing Sessions.
	 * @see org.springframework.data.gemfire.GemfireTemplate
	 * @see com.gemstone.gemfire.cache.Region
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public GemfireTemplate sessionRegionTemplate(GemFireCache gemFireCache) {
		return new GemfireTemplate(gemFireCache.getRegion(getSpringSessionGemFireRegionName()));
	}

	/**
	 * Defines a Spring GemFire {@link com.gemstone.gemfire.cache.Cache} {@link Region} bean used to store
	 * and manage Sessions using either a client-server or peer-to-peer (p2p) topology.
	 *
	 * @param gemfireCache a reference to the GemFire {@link com.gemstone.gemfire.cache.Cache}.
	 * @param sessionRegionAttributes the GemFire {@link RegionAttributes} used to configure the {@link Region}.
	 * @return a {@link GemFireCacheTypeAwareRegionFactoryBean} used to configure and initialize a GemFire Cache
	 * {@link Region} for storing and managing Sessions.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.GemFireCacheTypeAwareRegionFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.RegionAttributes
	 * @see #getClientRegionShortcut()
	 * @see #getSpringSessionGemFireRegionName()
	 * @see #getServerRegionShortcut()
	 */
	@Bean(name = DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public GemFireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession> sessionRegion(GemFireCache gemfireCache,
			RegionAttributes<Object, ExpiringSession> sessionRegionAttributes) {

		GemFireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession> serverRegion =
			new GemFireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession>();

		serverRegion.setGemfireCache(gemfireCache);
		serverRegion.setClientRegionShortcut(getClientRegionShortcut());
		serverRegion.setRegionAttributes(sessionRegionAttributes);
		serverRegion.setRegionName(getSpringSessionGemFireRegionName());
		serverRegion.setServerRegionShortcut(getServerRegionShortcut());

		return serverRegion;
	}

	/**
	 * Defines a Spring GemFire {@link RegionAttributes} bean used to configure and initialize the GemFire cache
	 * {@link Region} storing Sessions.  Expiration is also configured for the {@link Region} on the basis that the
	 * GemFire cache {@link Region} is a not a proxy, on either the client or server.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @return an instance of {@link RegionAttributes} used to configure and initialize the GemFire cache {@link Region}
	 * for storing and managing Sessions.
	 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.PartitionAttributes
	 * @see #isExpirationAllowed(GemFireCache)
	 */
	@Bean
	@SuppressWarnings({ "unchecked", "deprecation" })
	public RegionAttributesFactoryBean sessionRegionAttributes(GemFireCache gemfireCache) {
		RegionAttributesFactoryBean regionAttributes = new RegionAttributesFactoryBean();

		regionAttributes.setKeyConstraint(SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT);
		regionAttributes.setValueConstraint(SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT);

		if (isExpirationAllowed(gemfireCache)) {
			regionAttributes.setStatisticsEnabled(true);
			regionAttributes.setEntryIdleTimeout(new ExpirationAttributes(
				Math.max(getMaxInactiveIntervalInSeconds(), 0), ExpirationAction.INVALIDATE));
		}

		return regionAttributes;
	}

	/**
	 * Determines whether expiration configuration is allowed to be set on the GemFire cache {@link Region}
	 * used to store and manage Sessions.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @return a boolean indicating if a {@link Region} can be configured for Region entry idle-timeout expiration.
	 * @see GemFireUtils#isClient(GemFireCache)
	 * @see GemFireUtils#isProxy(ClientRegionShortcut)
	 * @see GemFireUtils#isProxy(RegionShortcut)
	 */
	boolean isExpirationAllowed(GemFireCache gemfireCache) {
		return !(GemFireUtils.isClient(gemfireCache) ? GemFireUtils.isProxy(getClientRegionShortcut())
			: GemFireUtils.isProxy(getServerRegionShortcut()));
	}

	/**
	 * Defines a Spring GemFire Index bean on the GemFire cache {@link Region} storing and managing Sessions,
	 * specifically on the 'principalName' property for quick lookup and queries. This index will only be created
	 * on a server @{link Region}.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @return a IndexFactoryBean creating an GemFire Index on the 'principalName' property of Sessions stored
	 * in the GemFire cache {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public IndexFactoryBean principalNameIndex(final GemFireCache gemfireCache) {
		IndexFactoryBean index = new IndexFactoryBean() {
			@Override public void afterPropertiesSet() throws Exception {
				if (GemFireUtils.isPeer(gemfireCache)) {
					super.afterPropertiesSet();
				}
			}
		};

		index.setCache(gemfireCache);
		index.setName("principalNameIdx");
		index.setExpression("principalName");
		index.setFrom(GemFireUtils.toRegionPath(getSpringSessionGemFireRegionName()));
		index.setOverride(true);
		index.setType(IndexType.HASH);

		return index;
	}

}
