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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.gemfire.GenericRegionFactoryBean;
import org.springframework.data.gemfire.client.ClientRegionFactoryBean;
import org.springframework.data.gemfire.client.Interest;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.InterestResultPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * The GemFireCacheTypeAwareRegionFactoryBean class is a Spring {@link FactoryBean} used to construct, configure
 * and initialize the GemFire cache {@link Region} used to store and manage Session state.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.FactoryBean
 * @see org.springframework.beans.factory.InitializingBean
 * @see org.springframework.data.gemfire.GenericRegionFactoryBean
 * @see org.springframework.data.gemfire.client.ClientRegionFactoryBean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.InterestResultPolicy
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.RegionAttributes
 * @see com.gemstone.gemfire.cache.RegionShortcut
 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
 * @since 1.1.0
 */
public class GemFireCacheTypeAwareRegionFactoryBean<K, V> implements FactoryBean<Region<K, V>>, InitializingBean {

	protected static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT;

	protected static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT =
		GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT;

	protected static final String DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME =
		GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME;

	private ClientRegionShortcut clientRegionShortcut;

	private GemFireCache gemfireCache;

	private Region<K, V> region;

	private RegionAttributes<K, V> regionAttributes;

	private RegionShortcut serverRegionShortcut;

	private String regionName;

	/**
	 * Post-construction initialization callback to create, configure and initialize the GemFire cache {@link Region}
	 * used to store, replicate (distribute) and manage Session state.  This method intelligently handles
	 * both client-server and peer-to-peer (p2p) GemFire supported distributed system topologies.
	 *
	 * @throws Exception if the initialization of the GemFire cache {@link Region} fails.
	 * @see org.springframework.session.data.gemfire.support.GemFireUtils#isClient(GemFireCache)
	 * @see #getGemfireCache()
	 * @see #newClientRegion(GemFireCache)
	 * @see #newServerRegion(GemFireCache)
	 */
	public void afterPropertiesSet() throws Exception {
		GemFireCache gemfireCache = getGemfireCache();

		region = (GemFireUtils.isClient(gemfireCache) ? newClientRegion(gemfireCache)
			: newServerRegion(gemfireCache));
	}

	/**
	 * Constructs a GemFire cache {@link Region} using a peer-to-peer (p2p) GemFire topology to store
	 * and manage Session state in a GemFire server cluster accessible from a GemFire cache client.
	 *
	 * @param gemfireCache a reference to the GemFire {@link com.gemstone.gemfire.cache.Cache}.
	 * @return a peer-to-peer-based GemFire cache {@link Region} to store and manage Session state.
	 * @throws Exception if the instantiation, configuration and initialization
	 * of the GemFire cache {@link Region} fails.
	 * @see org.springframework.data.gemfire.GenericRegionFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.Region
	 * @see #getRegionAttributes()
	 * @see #getRegionName()
	 * @see #getServerRegionShortcut()
	 */
	protected Region<K, V> newServerRegion(GemFireCache gemfireCache) throws Exception {
		GenericRegionFactoryBean<K, V> serverRegion = new GenericRegionFactoryBean<K, V>();

		serverRegion.setCache(gemfireCache);
		serverRegion.setAttributes(getRegionAttributes());
		serverRegion.setRegionName(getRegionName());
		serverRegion.setShortcut(getServerRegionShortcut());
		serverRegion.afterPropertiesSet();

		return serverRegion.getObject();
	}

	/**
	 * Constructs a GemFire cache {@link Region} using the client-server GemFire topology to store
	 * and manage Session state in a GemFire server cluster accessible from a GemFire cache client.
	 *
	 * @param gemfireCache a reference to the GemFire {@link com.gemstone.gemfire.cache.Cache}.
	 * @return a client-server-based GemFire cache {@link Region} to store and manage Session state.
	 * @throws Exception if the instantiation, configuration and initialization
	 * of the GemFire cache {@link Region} fails.
	 * @see org.springframework.data.gemfire.client.ClientRegionFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.Region
	 * @see #getClientRegionShortcut()
	 * @see #getRegionAttributes()
	 * @see #getRegionName()
	 * @see #registerInterests(boolean)
	 */
	protected Region<K, V> newClientRegion(GemFireCache gemfireCache) throws Exception {
		ClientRegionFactoryBean<K, V> clientRegion = new ClientRegionFactoryBean<K, V>();

		ClientRegionShortcut shortcut = getClientRegionShortcut();

		clientRegion.setCache(gemfireCache);
		clientRegion.setAttributes(getRegionAttributes());
		clientRegion.setInterests(registerInterests(!GemFireUtils.isLocal(shortcut)));
		clientRegion.setRegionName(getRegionName());
		clientRegion.setShortcut(shortcut);
		clientRegion.afterPropertiesSet();

		return clientRegion.getObject();
	}

	/**
	 * Registers interests in all keys when the client {@link Region} is non-local.
	 *
	 * @return an array of Interests specifying the server notifications of interests to the client.
	 * @see org.springframework.data.gemfire.client.Interest
	 */
	/**
	 * Decides whether interests will be registered for all keys.  Interests is only registered on a client
	 * and typically only when the client is a (CACHING) PROXY to the server (i.e. non-LOCAL only).
	 *
	 * @param register a boolean value indicating whether interests should be registered.
	 * @return an array of Interests KEY/VALUE registrations.
	 * @see org.springframework.data.gemfire.client.Interest
	 */
	@SuppressWarnings("unchecked")
	protected Interest<K>[] registerInterests(boolean register) {
		return (!register ? new Interest[0] : new Interest[] {
			new Interest<String>("ALL_KEYS", InterestResultPolicy.KEYS)
		});
	}

	/**
	 * Returns a reference to the constructed GemFire cache {@link Region} used to store and manage Session state.
	 *
	 * @return the {@link Region} used to store and manage Session state.
	 * @throws Exception if the {@link Region} reference cannot be obtained.
	 * @see com.gemstone.gemfire.cache.Region
	 */
	public Region<K, V> getObject() throws Exception {
		return region;
	}

	/**
	 * Returns the specific type of GemFire cache {@link Region} this factory creates when initialized
	 * or Region.class when uninitialized.
	 *
	 * @return the GemFire cache {@link Region} class type constructed by this factory.
	 * @see com.gemstone.gemfire.cache.Region
	 * @see java.lang.Class
	 */
	public Class<?> getObjectType() {
		return (region != null ? region.getClass() : Region.class);
	}

	/**
	 * Returns true indicating the GemFire cache {@link Region} created by this factory is the sole instance.
	 *
	 * @return true to indicate the GemFire cache {@link Region} storing and managing Sessions is a Singleton.
	 */
	public boolean isSingleton() {
		return true;
	}

	/**
	 * Sets the {@link Region} data policy used by the GemFire cache client to manage Session state.
	 *
	 * @param clientRegionShortcut a {@link ClientRegionShortcut} to specify the client {@link Region}
	 * data management policy.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	public void setClientRegionShortcut(ClientRegionShortcut clientRegionShortcut) {
		this.clientRegionShortcut = clientRegionShortcut;
	}

	/**
	 * Returns the {@link Region} data policy used by the GemFire cache client to manage Session state.  Defaults to
	 * {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return a {@link ClientRegionShortcut} specifying the client {@link Region} data management policy.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration#DEFAULT_CLIENT_REGION_SHORTCUT
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	protected ClientRegionShortcut getClientRegionShortcut() {
		return (clientRegionShortcut != null ? clientRegionShortcut : DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
	 * Sets a reference to the GemFire cache used to construct the appropriate {@link Region}.
	 *
	 * @param gemfireCache a reference to the GemFire cache.
	 * @throws IllegalArgumentException if the {@link GemFireCache} reference is null.
	 */
	public void setGemfireCache(GemFireCache gemfireCache) {
		Assert.notNull(gemfireCache, "The GemFireCache reference must not be null");
		this.gemfireCache = gemfireCache;
	}

	/**
	 * Returns a reference to the GemFire cache used to construct the appropriate {@link Region}.
	 *
	 * @return a reference to the GemFire cache.
	 * @throws IllegalStateException if the {@link GemFireCache} reference is null.
	 */
	protected GemFireCache getGemfireCache() {
		Assert.state(gemfireCache != null, "A reference to a GemFireCache was not properly configured");
		return gemfireCache;
	}

	/**
	 * Sets the GemFire {@link RegionAttributes} used to configure the GemFire cache {@link Region} used to
	 * store and manage Session state.
	 *
	 * @param regionAttributes the GemFire {@link RegionAttributes} used to configure the GemFire cache {@link Region}.
	 * @see com.gemstone.gemfire.cache.RegionAttributes
	 */
	public void setRegionAttributes(RegionAttributes<K, V> regionAttributes) {
		this.regionAttributes = regionAttributes;
	}

	/**
	 * Returns the GemFire {@link RegionAttributes} used to configure the GemFire cache {@link Region} used to
	 * store and manage Session state.
	 *
	 * @return the GemFire {@link RegionAttributes} used to configure the GemFire cache {@link Region}.
	 * @see com.gemstone.gemfire.cache.RegionAttributes
	 */
	protected RegionAttributes<K, V> getRegionAttributes() {
		return regionAttributes;
	}

	/**
	 * Sets the name of the GemFire cache {@link Region} use to store and manage Session state.
	 *
	 * @param regionName a String specifying the name of the GemFire cache {@link Region}.
	 */
	public void setRegionName(final String regionName) {
		this.regionName = regionName;
	}

	/**
	 * Returns the configured name of the GemFire cache {@link Region} use to store and manage Session state.
	 * Defaults to "ClusteredSpringSessions"
	 *
	 * @return a String specifying the name of the GemFire cache {@link Region}.
	 * @see com.gemstone.gemfire.cache.Region#getName()
	 */
	protected String getRegionName() {
		return (StringUtils.hasText(regionName) ? regionName : DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	/**
	 * Sets the {@link Region} data policy used by the GemFire peer cache to manage Session state.
	 *
	 * @param serverRegionShortcut a {@link RegionShortcut} to specify the peer {@link Region} data management policy.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	public void setServerRegionShortcut(RegionShortcut serverRegionShortcut) {
		this.serverRegionShortcut = serverRegionShortcut;
	}

	/**
	 * Returns the {@link Region} data policy used by the GemFire peer cache to manage Session state. Defaults to
	 * {@link RegionShortcut#PARTITION}.
	 *
	 * @return a {@link RegionShortcut} specifying the peer {@link Region} data management policy.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	protected RegionShortcut getServerRegionShortcut() {
		return (serverRegionShortcut != null ? serverRegionShortcut : DEFAULT_SERVER_REGION_SHORTCUT);
	}

}
