/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.web;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.SpringProperties;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.web.ProjectingJackson2HttpMessageConverterUnitTests.SampleInterface;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Unit tests for {@link ProxyingHandlerMethodArgumentResolver}.
 * 
 * @author Oliver Gierke
 * @soundtrack Karlijn Langendijk & Sönke Meinen - Englishman In New York (Sting,
 *             https://www.youtube.com/watch?v=O7LZsqrnaaA)
 */
public class ProxyingHandlerMethodArgumentResolverUnitTests {

	ObjectFactory<ConversionService> conversionService = new ObjectFactory<ConversionService>() {

		@Override
		public ConversionService getObject() {
			return new DefaultConversionService();
		}
	};

	ProxyingHandlerMethodArgumentResolver resolver = new ProxyingHandlerMethodArgumentResolver(conversionService, true);

	@Test // DATACMNS-776
	public void supportAnnotatedInterface() throws Exception {

		Method method = Controller.class.getMethod("with", AnnotatedInterface.class);
		MethodParameter parameter = new MethodParameter(method, 0);

		assertThat(resolver.supportsParameter(parameter), is(true));
	}

	@Test // DATACMNS-776
	public void supportsUnannotatedInterfaceFromUserPackage() throws Exception {

		Method method = Controller.class.getMethod("with", SampleInterface.class);
		MethodParameter parameter = new MethodParameter(method, 0);

		assertThat(resolver.supportsParameter(parameter), is(true));
	}

	@Test // DATACMNS-776
	public void doesNotSupportUnannotatedInterfaceFromSpringNamespace() throws Exception {

		Method method = Controller.class.getMethod("with", UnannotatedInterface.class);
		MethodParameter parameter = new MethodParameter(method, 0);

		assertThat(resolver.supportsParameter(parameter), is(false));
	}

	@Test // DATACMNS-776
	public void doesNotSupportCoreJavaType() throws Exception {

		Method method = Controller.class.getMethod("with", List.class);
		MethodParameter parameter = new MethodParameter(method, 0);

		assertThat(resolver.supportsParameter(parameter), is(false));
	}

	@Test // GH-3495
	public void bindsCollectionValuesWithinDefaultLimit() throws Exception {
		assertThat(bindFooBar(resolver, 1023), hasSize(1024));
	}

	@Test // GH-3495
	public void doesNotGrowCollectionsBeyondDefaultLimitOnBinding() throws Exception {
		assertNullOrEmpty(bindFooBar(resolver, 10000000));
	}

	@Test // GH-3495
	public void considersCollectionLimitConfiguredViaSpringProperties() throws Exception {

		SpringProperties.setProperty(ProxyingHandlerMethodArgumentResolver.COLLECTION_SIZE_LIMIT_PARAM, "10");

		try {

			ProxyingHandlerMethodArgumentResolver customized = new ProxyingHandlerMethodArgumentResolver(
					conversionService, true);

			assertThat(bindFooBar(customized, 9), hasSize(10));
			assertNullOrEmpty(bindFooBar(customized, 10));

		} finally {
			SpringProperties.setProperty(ProxyingHandlerMethodArgumentResolver.COLLECTION_SIZE_LIMIT_PARAM, null);
		}
	}

	private static List<String> bindFooBar(ProxyingHandlerMethodArgumentResolver resolver, int index)
			throws Exception {

		MethodParameter parameter = new MethodParameter(Controller.class.getMethod("with", CollectionPayload.class), 0);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addParameter(String.format("fooBar[%s]", index), "Spring");

		CollectionPayload payload = (CollectionPayload) resolver.createAttribute("payload", parameter, null,
				new ServletWebRequest(request));

		return payload.getFooBar();
	}

	private static void assertNullOrEmpty(List<String> values) {
		assertThat(values == null || values.isEmpty(), is(true));
	}

	@ProjectedPayload
	interface AnnotatedInterface {}

	interface UnannotatedInterface {}

	@ProjectedPayload
	interface CollectionPayload {
		List<String> getFooBar();
	}

	interface Controller {

		void with(AnnotatedInterface param);

		void with(UnannotatedInterface param);

		void with(SampleInterface param);

		void with(CollectionPayload param);

		void with(List<Object> param);
	}
}
