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
package org.springframework.session.data.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.UnsupportedEncodingException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;


/**
 *
 * @author Rob Winch
 * @author Mark Anderson
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionMessageListenerTests {
	@Mock
	ApplicationEventPublisher eventPublisher;

	@Mock
	Message message;

	@Captor
	ArgumentCaptor<SessionDestroyedEvent> deletedEvent;

	@Captor
	ArgumentCaptor<SessionExpiredEvent> expiredEvent;

	byte[] pattern;

	SessionMessageListener listener;

	@Before
	public void setup() {
		listener = new SessionMessageListener(eventPublisher);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorNullEventPublisher() {
		new SessionMessageListener(null);
	}

	@Test
	public void onMessageNullBody() throws Exception {
		listener.onMessage(message, pattern);

		verifyZeroInteractions(eventPublisher);
	}

	@Test
	public void onMessageDel() throws Exception {
		mockMessage("__keyevent@0__:del", "spring:session:sessions:123");

		listener.onMessage(message, pattern);

		verify(eventPublisher).publishEvent(deletedEvent.capture());
		assertThat(deletedEvent.getValue().getSessionId()).isEqualTo("123");
	}

	@Test
	public void onMessageDelSource() throws Exception {
		mockMessage("__keyevent@0__:del","spring:session:sessions:123");

		listener.onMessage(message, pattern);

		verify(eventPublisher).publishEvent(deletedEvent.capture());
		assertThat(deletedEvent.getValue().getSource()).isEqualTo(listener);
	}

	@Test
	public void onMessageExpiredSource() throws Exception {
		mockMessage("__keyevent@0__:expired","spring:session:sessions:123");

		listener.onMessage(message, pattern);

		verify(eventPublisher).publishEvent(expiredEvent.capture());
		assertThat(expiredEvent.getValue().getSource()).isEqualTo(listener);
	}

	@Test
	public void onMessageExpired() throws Exception {
		mockMessage("__keyevent@0__:expired","spring:session:sessions:543");

		listener.onMessage(message, pattern);

		verify(eventPublisher).publishEvent(expiredEvent.capture());
		assertThat(expiredEvent.getValue().getSessionId()).isEqualTo("543");
	}

	@Test
	public void onMessageHset() throws Exception {
		mockMessage("__keyevent@0__:hset","spring:session:sessions:123");

		listener.onMessage(message, pattern);

		verifyZeroInteractions(eventPublisher);
	}

	@Test
	public void onMessageWrongKeyPrefix() throws Exception {
		mockMessage("__keyevent@0__:del","spring:session:sessionsNo:123");

		listener.onMessage(message, pattern);

		verifyZeroInteractions(eventPublisher);
	}

	@Test
	public void onMessageRename() throws Exception {
		mockMessage("__keyevent@0__:rename","spring:session:sessions:123");

		listener.onMessage(message, pattern);

		verifyZeroInteractions(eventPublisher);
	}

	@Test
	public void onMessageEventPublisherErrorCaught() throws Exception {
		mockMessage("__keyevent@0__:del","spring:session:sessions:123");
		doThrow(new IllegalStateException("Test Exceptions are caught")).when(eventPublisher).publishEvent(any(ApplicationEvent.class));

		listener.onMessage(message, pattern);

		verify(eventPublisher).publishEvent(any(ApplicationEvent.class));
	}

	private void mockMessage(String channel, String body) throws UnsupportedEncodingException {
		when(message.getBody()).thenReturn(bytes(body));
		when(message.getChannel()).thenReturn(bytes(channel));
	}

	private static byte[] bytes(String s) throws UnsupportedEncodingException {
		return s.getBytes("UTF-8");
	}
}
