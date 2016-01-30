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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.gemstone.gemfire.DataSerializable;
import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.Delta;
import com.gemstone.gemfire.Instantiator;
import com.gemstone.gemfire.InvalidDeltaException;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.util.CacheListenerAdapter;

/**
 * AbstractGemFireOperationsSessionRepository is an abstract base class encapsulating functionality common
 * to all implementations that support SessionRepository operations backed by GemFire.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.InitializingBean
 * @see org.springframework.context.ApplicationEventPublisher
 * @see org.springframework.context.ApplicationEventPublisherAware
 * @see org.springframework.data.gemfire.GemfireAccessor
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.FindByIndexNameSessionRepository
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.util.CacheListenerAdapter
 * @since 1.1.0
 */
public abstract class AbstractGemFireOperationsSessionRepository extends CacheListenerAdapter<Object, ExpiringSession>
		implements InitializingBean, FindByIndexNameSessionRepository<ExpiringSession>,
			ApplicationEventPublisherAware {

	private int maxInactiveIntervalInSeconds = GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;

	private ApplicationEventPublisher applicationEventPublisher = new ApplicationEventPublisher() {
		public void publishEvent(ApplicationEvent event) {
		}
	};

	private final GemfireOperations template;

	protected final Log logger = newLogger();

	private String fullyQualifiedRegionName;

	/**
	 * Constructs an instance of AbstractGemFireOperationsSessionRepository with a required GemfireOperations instance
	 * used to perform GemFire data access operations and interactions supporting the SessionRepository operations.
	 *
	 * @param template the GemfireOperations instance used to interact with GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public AbstractGemFireOperationsSessionRepository(GemfireOperations template) {
		Assert.notNull(template, "GemfireOperations must not be null");
		this.template = template;
	}

	/**
	 * Used for testing purposes only to override the Log implementation with a mock.
	 *
	 * @return an instance of Log constructed from Apache commons-logging LogFactory.
	 * @see org.apache.commons.logging.LogFactory#getLog(Class)
	 */
	Log newLogger() {
		return LogFactory.getLog(getClass());
	}

	/**
	 * Sets the ApplicationEventPublisher used to publish Session events corresponding to GemFire cache events.
	 *
	 * @param applicationEventPublisher the Spring ApplicationEventPublisher used to publish Session-based events.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher must not be null");
		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 * Gets the ApplicationEventPublisher used to publish Session events corresponding to GemFire cache events.
	 *
	 * @return the Spring ApplicationEventPublisher used to publish Session-based events.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	protected ApplicationEventPublisher getApplicationEventPublisher() {
		return applicationEventPublisher;
	}

	/**
	 * Gets the fully-qualified name of the GemFire cache {@link Region} used to store and manage Session data.
	 *
	 * @return a String indicating the fully qualified name of the GemFire cache {@link Region} used to store
	 * and manage Session data.
	 */
	protected String getFullyQualifiedRegionName() {
		return fullyQualifiedRegionName;
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
	public int getMaxInactiveIntervalInSeconds() {
		return maxInactiveIntervalInSeconds;
	}

	/**
	 * Gets a reference to the GemfireOperations (template) used to perform data access operations
	 * and other interactions on the GemFire cache {@link Region} backing this SessionRepository.
	 *
	 * @return a reference to the GemfireOperations used to interact with GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemfireOperations getTemplate() {
		return template;
	}

	/**
	 * Callback method during Spring bean initialization that will capture the fully-qualified name
	 * of the GemFire cache {@link Region} used to manage Session state and register this SessionRepository
	 * as a GemFire {@link com.gemstone.gemfire.cache.CacheListener}.
	 *
	 * @throws Exception if an error occurs during the initialization process.
	 */
	public void afterPropertiesSet() throws Exception {
		GemfireOperations template = getTemplate();

		Assert.isInstanceOf(GemfireAccessor.class, template);

		Region<Object, ExpiringSession> region = ((GemfireAccessor) template).getRegion();

		fullyQualifiedRegionName = region.getFullPath();
		region.getAttributesMutator().addCacheListener(this);
	}

	/**
	 * Callback method triggered when an entry is created in the GemFire cache {@link Region}.
	 *
	 * @param event an EntryEvent containing the details of the cache operation.
	 * @see com.gemstone.gemfire.cache.EntryEvent
	 * @see #handleCreated(String, ExpiringSession)
	 */
	@Override
	public void afterCreate(EntryEvent<Object, ExpiringSession> event) {
		handleCreated(event.getKey().toString(), event.getNewValue());
	}

	/**
	 * Callback method triggered when an entry is destroyed in the GemFire cache {@link Region}.
	 *
	 * @param event an EntryEvent containing the details of the cache operation.
	 * @see com.gemstone.gemfire.cache.EntryEvent
	 * @see #handleDestroyed(String, ExpiringSession)
	 */
	@Override
	public void afterDestroy(EntryEvent<Object, ExpiringSession> event) {
		handleDestroyed(event.getKey().toString(), event.getOldValue());
	}

	/**
	 * Callback method triggered when an entry is invalidated in the GemFire cache {@link Region}.
	 *
	 * @param event an EntryEvent containing the details of the cache operation.
	 * @see com.gemstone.gemfire.cache.EntryEvent
	 * @see #handleExpired(String, ExpiringSession)
	 */
	@Override
	public void afterInvalidate(EntryEvent<Object, ExpiringSession> event) {
		handleExpired(event.getKey().toString(), event.getOldValue());
	}

	/**
	 * Causes Session created events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionCreatedEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleCreated(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionCreatedEvent(this, session)
			: new SessionCreatedEvent(this, sessionId));
	}

	/**
	 * Causes Session deleted events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionDeletedEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleDeleted(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionDeletedEvent(this, session)
			: new SessionDeletedEvent(this, sessionId));
	}

	/**
	 * Causes Session destroyed events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionDestroyedEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleDestroyed(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionDestroyedEvent(this, session)
			: new SessionDestroyedEvent(this, sessionId));
	}

	/**
	 * Causes Session expired events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionExpiredEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleExpired(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionExpiredEvent(this, session)
			: new SessionExpiredEvent(this, sessionId));
	}

	/**
	 * Publishes the specified ApplicationEvent to the Spring application context.
	 *
	 * @param event the ApplicationEvent to publish.
	 * @see org.springframework.context.ApplicationEventPublisher#publishEvent(ApplicationEvent)
	 * @see org.springframework.context.ApplicationEvent
	 */
	protected void publishEvent(ApplicationEvent event) {
		try {
			getApplicationEventPublisher().publishEvent(event);
		}
		catch (Throwable t) {
			logger.error(String.format("error occurred publishing event (%1$s)", event), t);
		}
	}

	/**
	 * GemFireSession is a GemFire representation model of a Spring {@link ExpiringSession} for storing and accessing
	 * Session state information in GemFire.  This class implements GemFire's {@link DataSerializable} interface
	 * to better handle replication of Session information across the GemFire cluster.
	 *
	 * @see java.lang.Comparable
	 * @see org.springframework.session.ExpiringSession
	 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes
	 * @see com.gemstone.gemfire.DataSerializable
	 * @see com.gemstone.gemfire.DataSerializer
	 * @see com.gemstone.gemfire.Delta
	 * @see com.gemstone.gemfire.Instantiator
	 */
	@SuppressWarnings("serial")
	public static class GemFireSession implements Comparable<ExpiringSession>, DataSerializable, Delta, ExpiringSession {

		protected static final boolean DEFAULT_ALLOW_JAVA_SERIALIZATION = true;

		protected static final DateFormat TO_STRING_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-dd-HH-mm-ss");

		static {
			Instantiator.register(new Instantiator(GemFireSession.class, 800813552) {
				@Override public DataSerializable newInstance() {
					return new GemFireSession();
				}
			});
		}

		private String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

		private SpelExpressionParser parser = new SpelExpressionParser();

		private transient boolean delta = false;

		private int maxInactiveIntervalInSeconds;

		private long creationTime;
		private long lastAccessedTime;

		private transient final GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes(this);

		private String id;

		/* (non-Javadoc) */
		protected GemFireSession() {
			this(UUID.randomUUID().toString());
		}

		/* (non-Javadoc) */
		protected GemFireSession(String id) {
			this.id = validateId(id);
			this.creationTime = System.currentTimeMillis();
			this.lastAccessedTime = this.creationTime;
		}

		/* (non-Javadoc) */
		protected GemFireSession(ExpiringSession session) {
			Assert.notNull(session, "The ExpiringSession to copy cannot be null");

			this.id = session.getId();
			this.creationTime = session.getCreationTime();
			this.lastAccessedTime = session.getLastAccessedTime();
			this.maxInactiveIntervalInSeconds = session.getMaxInactiveIntervalInSeconds();
			this.sessionAttributes.from(session);
		}

		/* (non-Javadoc) */
		public static GemFireSession create(int maxInactiveIntervalInSeconds) {
			GemFireSession session = new GemFireSession();
			session.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds);
			return session;
		}

		/* (non-Javadoc) */
		public static GemFireSession from(ExpiringSession expiringSession) {
			GemFireSession session = new GemFireSession(expiringSession);
			session.setLastAccessedTime(System.currentTimeMillis());
			return session;
		}

		/* (non-Javadoc) */
		private String validateId(String id) {
			Assert.hasText(id, "ID must be specified");
			return id;
		}

		/* (non-Javadoc) */
		protected boolean allowJavaSerialization() {
			return DEFAULT_ALLOW_JAVA_SERIALIZATION;
		}

		/* (non-Javadoc) */
		public synchronized String getId() {
			return id;
		}

		/* (non-Javadoc) */
		public synchronized long getCreationTime() {
			return creationTime;
		}

		/* (non-Javadoc) */
		public void setAttribute(String attributeName, Object attributeValue) {
			sessionAttributes.setAttribute(attributeName, attributeValue);
		}

		/* (non-Javadoc) */
		public void removeAttribute(String attributeName) {
			sessionAttributes.removeAttribute(attributeName);
		}

		/* (non-Javadoc) */
		public <T> T getAttribute(String attributeName) {
			return sessionAttributes.getAttribute(attributeName);
		}

		/* (non-Javadoc) */
		public Set<String> getAttributeNames() {
			return sessionAttributes.getAttributeNames();
		}

		/* (non-Javadoc) */
		public synchronized boolean isExpired() {
			long lastAccessedTime = getLastAccessedTime();
			long maxInactiveIntervalInSeconds = getMaxInactiveIntervalInSeconds();

			return (maxInactiveIntervalInSeconds >= 0
				&& (idleTimeout(maxInactiveIntervalInSeconds) >= lastAccessedTime));
		}

		/* (non-Javadoc) */
		private long idleTimeout(long maxInactiveIntervalInSeconds) {
			return (System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(maxInactiveIntervalInSeconds));
		}

		/* (non-Javadoc) */
		public synchronized void setLastAccessedTime(long lastAccessedTime) {
			this.delta |= (this.lastAccessedTime != lastAccessedTime);
			this.lastAccessedTime = lastAccessedTime;
		}

		/* (non-Javadoc) */
		public synchronized long getLastAccessedTime() {
			return lastAccessedTime;
		}

		/* (non-Javadoc) */
		public synchronized void setMaxInactiveIntervalInSeconds(final int maxInactiveIntervalInSeconds) {
			this.delta |= (this.maxInactiveIntervalInSeconds != maxInactiveIntervalInSeconds);
			this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
		}

		/* (non-Javadoc) */
		public synchronized int getMaxInactiveIntervalInSeconds() {
			return maxInactiveIntervalInSeconds;
		}

		/* (non-Javadoc) */
		public synchronized void setPrincipalName(String principalName) {
			setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principalName);
		}

		/* (non-Javadoc) */
		public synchronized String getPrincipalName() {
			String principalName = getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
			if(principalName != null) {
				return principalName;
			}
			Object authentication = getAttribute(SPRING_SECURITY_CONTEXT);
			if(authentication != null) {
				Expression expression = parser.parseExpression("authentication?.name");
				return expression.getValue(authentication, String.class);
			}
			return null;
		}

		/* (non-Javadoc) */
		public synchronized void toData(DataOutput out) throws IOException {
			out.writeUTF(getId());
			out.writeLong(getCreationTime());
			out.writeLong(getLastAccessedTime());
			out.writeInt(getMaxInactiveIntervalInSeconds());

			String principalName = getPrincipalName();
			int length = (StringUtils.hasText(principalName) ? principalName.length() : 0);

			out.writeInt(length);

			if (length > 0) {
				out.writeUTF(principalName);
			}

			writeObject(sessionAttributes, out);

			this.delta = false;
		}

		/* (non-Javadoc) */
		void writeObject(Object obj, DataOutput out) throws IOException {
			DataSerializer.writeObject(obj, out, allowJavaSerialization());
		}

		/* (non-Javadoc) */
		public synchronized void fromData(DataInput in) throws ClassNotFoundException, IOException {
			id = in.readUTF();
			creationTime = in.readLong();
			setLastAccessedTime(in.readLong());
			setMaxInactiveIntervalInSeconds(in.readInt());

			int principalNameLength = in.readInt();

			if (principalNameLength > 0) {
				setPrincipalName(in.readUTF());
			}

			sessionAttributes.from(this.<GemFireSessionAttributes>readObject(in));

			this.delta = false;
		}

		/* (non-Javadoc) */
		<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
			return DataSerializer.readObject(in);
		}

		/* (non-Javadoc) */
		public synchronized boolean hasDelta() {
			return (delta || sessionAttributes.hasDelta());
		}

		/* (non-Javadoc) */
		public synchronized void toDelta(DataOutput out) throws IOException {
			out.writeLong(getLastAccessedTime());
			out.writeInt(getMaxInactiveIntervalInSeconds());
			sessionAttributes.toDelta(out);
			this.delta = false;
		}

		/* (non-Javadoc) */
		public synchronized void fromDelta(DataInput in) throws IOException {
			setLastAccessedTime(in.readLong());
			setMaxInactiveIntervalInSeconds(in.readInt());
			sessionAttributes.fromDelta(in);
			this.delta = false;
		}

		/* (non-Javadoc) */
		@SuppressWarnings("all")
		public int compareTo(ExpiringSession session) {
			return (Long.valueOf(getCreationTime()).compareTo(session.getCreationTime()));
		}

		/* (non-Javadoc) */
		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}

			if (!(obj instanceof Session)) {
				return false;
			}

			Session that = (Session) obj;

			return this.getId().equals(that.getId());
		}

		/* (non-Javadoc) */
		@Override
		public int hashCode() {
			int hashValue = 17;
			hashValue = 37 * hashValue + getId().hashCode();
			return hashValue;
		}

		/* (non-Javadoc) */
		@Override
		public synchronized String toString() {
			return String.format("{ @type = %1$s, id = %2$s, creationTime = %3$s, lastAccessedTime = %4$s"
				+ ", maxInactiveIntervalInSeconds = %5$s, principalName = %6$s }", getClass().getName(), getId(),
					toString(getCreationTime()), toString(getLastAccessedTime()), getMaxInactiveIntervalInSeconds(),
						getPrincipalName());
		}

		/* (non-Javadoc) */
		private String toString(long timestamp) {
			return TO_STRING_DATE_FORMAT.format(new Date(timestamp));
		}
	}

	/**
	 * The GemFireSessionAttributes class is a container for a Session attributes that implements both
	 * the {@link DataSerializable} and {@link Delta} GemFire interfaces for efficient storage and distribution
	 * (replication) in GemFire.
	 *
	 * @see com.gemstone.gemfire.DataSerializable
	 * @see com.gemstone.gemfire.DataSerializer
	 * @see com.gemstone.gemfire.Delta
	 * @see com.gemstone.gemfire.Instantiator
	 */
	@SuppressWarnings("serial")
	public static class GemFireSessionAttributes implements DataSerializable, Delta {

		protected static final boolean DEFAULT_ALLOW_JAVA_SERIALIZATION = true;

		static {
			Instantiator.register(new Instantiator(GemFireSessionAttributes.class, 800828008) {
				@Override public DataSerializable newInstance() {
					return new GemFireSessionAttributes();
				}
			});
		}

		private transient final Map<String, Object> sessionAttributes = new HashMap<String, Object>();
		private transient final Map<String, Object> sessionAttributeDeltas = new HashMap<String, Object>();

		private transient final Object lock;

		/* (non-Javadoc) */
		protected GemFireSessionAttributes() {
			this.lock = this;
		}

		/* (non-Javadoc) */
		protected GemFireSessionAttributes(Object lock) {
			this.lock = (lock != null ? lock : this);
		}

		/* (non-Javadoc) */
		public void setAttribute(String attributeName, Object attributeValue) {
			synchronized (lock) {
				if (attributeValue != null) {
					if (!attributeValue.equals(sessionAttributes.put(attributeName, attributeValue))) {
						sessionAttributeDeltas.put(attributeName, attributeValue);
					}
				}
				else {
					removeAttribute(attributeName);
				}
			}
		}

		/* (non-Javadoc) */
		public void removeAttribute(String attributeName) {
			synchronized (lock) {
				if (sessionAttributes.remove(attributeName) != null) {
					sessionAttributeDeltas.put(attributeName, null);
				}
			}
		}

		/* (non-Javadoc) */
		@SuppressWarnings("unchecked")
		public <T> T getAttribute(String attributeName) {
			synchronized (lock) {
				return (T) sessionAttributes.get(attributeName);
			}
		}

		/* (non-Javadoc) */
		public Set<String> getAttributeNames() {
			synchronized (lock) {
				return Collections.unmodifiableSet(new HashSet<String>(sessionAttributes.keySet()));
			}
		}

		/* (non-Javadoc) */
		protected boolean allowJavaSerialization() {
			return DEFAULT_ALLOW_JAVA_SERIALIZATION;
		}

		/* (non-Javadoc) */
		public void from(Session session) {
			synchronized (lock) {
				for (String attributeName : session.getAttributeNames()) {
					setAttribute(attributeName, session.getAttribute(attributeName));
				}
			}
		}

		/* (non-Javadoc) */
		public void from(GemFireSessionAttributes sessionAttributes) {
			synchronized (lock) {
				for (String attributeName : sessionAttributes.getAttributeNames()) {
					setAttribute(attributeName, sessionAttributes.getAttribute(attributeName));
				}
			}
		}

		/* (non-Javadoc) */
		public void toData(DataOutput out) throws IOException {
			synchronized (lock) {
				Set<String> attributeNames = getAttributeNames();

				out.writeInt(attributeNames.size());

				for (String attributeName : attributeNames) {
					out.writeUTF(attributeName);
					writeObject(getAttribute(attributeName), out);
				}
			}
		}

		/* (non-Javadoc) */
		void writeObject(Object obj, DataOutput out) throws IOException {
			DataSerializer.writeObject(obj, out, allowJavaSerialization());
		}

		/* (non-Javadoc) */
		public void fromData(DataInput in) throws IOException, ClassNotFoundException {
			synchronized (lock) {
				for (int count = in.readInt(); count > 0; count--) {
					setAttribute(in.readUTF(), readObject(in));
				}

				sessionAttributeDeltas.clear();
			}
		}

		/* (non-Javadoc) */
		<T> T readObject(DataInput in) throws ClassNotFoundException , IOException {
			return DataSerializer.readObject(in);
		}

		/* (non-Javadoc) */
		public boolean hasDelta() {
			synchronized (lock) {
				return !sessionAttributeDeltas.isEmpty();
			}
		}

		/* (non-Javadoc) */
		public void toDelta(DataOutput out) throws IOException {
			synchronized (lock) {
				out.writeInt(sessionAttributeDeltas.size());

				for (Map.Entry<String, Object> entry : sessionAttributeDeltas.entrySet()) {
					out.writeUTF(entry.getKey());
					writeObject(entry.getValue(), out);
				}

				sessionAttributeDeltas.clear();
			}
		}

		/* (non-Javadoc) */
		public void fromDelta(DataInput in) throws InvalidDeltaException, IOException {
			synchronized (lock) {
				try {
					int count = in.readInt();

					Map<String, Object> deltas = new HashMap<String, Object>(count);

					while (count-- > 0) {
						deltas.put(in.readUTF(), readObject(in));
					}

					for (Map.Entry<String, Object> entry : deltas.entrySet()) {
						setAttribute(entry.getKey(), entry.getValue());
						sessionAttributeDeltas.remove(entry.getKey());
					}
				}
				catch (ClassNotFoundException e) {
					throw new InvalidDeltaException("class type in data not found", e);
				}
			}
		}

		@Override
		public String toString() {
			return sessionAttributes.toString();
		}
	}

}
