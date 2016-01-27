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
package org.springframework.session.web.socket.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.web.socket.events.SessionConnectEvent;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

@RunWith(MockitoJUnitRunner.class)
public class WebSocketConnectHandlerDecoratorFactoryTests {
	@Mock
	ApplicationEventPublisher eventPublisher;
	@Mock
	WebSocketHandler delegate;
	@Mock
	WebSocketSession session;
	@Captor
	ArgumentCaptor<SessionConnectEvent> event;

	WebSocketConnectHandlerDecoratorFactory factory;

	@Before
	public void setup() {
		factory = new WebSocketConnectHandlerDecoratorFactory(eventPublisher);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructorNullEventPublisher() {
		new WebSocketConnectHandlerDecoratorFactory(null);
	}

	@Test
	public void decorateAfterConnectionEstablished() throws Exception {
		WebSocketHandler decorated = factory.decorate(delegate);

		decorated.afterConnectionEstablished(session);

		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().getWebSocketSession()).isSameAs(session);
	}

	@Test
	public void decorateAfterConnectionEstablishedEventError() throws Exception {
		WebSocketHandler decorated = factory.decorate(delegate);
		doThrow(new IllegalStateException("Test throw on publishEvent")).when(eventPublisher).publishEvent(any(ApplicationEvent.class));

		decorated.afterConnectionEstablished(session);

		verify(eventPublisher).publishEvent(any(SessionConnectEvent.class));
	}
}
