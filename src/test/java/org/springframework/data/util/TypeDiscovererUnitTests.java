/*
 * Copyright 2011-2019 the original author or authors.
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
package org.springframework.data.util;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.util.ClassTypeInformation.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.FieldFilter;

/**
 * Unit tests for {@link TypeDiscoverer}.
 * 
 * @author Oliver Gierke
 */
@RunWith(MockitoJUnitRunner.class)
public class TypeDiscovererUnitTests {

	static final Map<TypeVariable<?>, Type> EMPTY_MAP = Collections.emptyMap();

	@Mock Map<TypeVariable<?>, Type> firstMap;
	@Mock Map<TypeVariable<?>, Type> secondMap;

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullType() {
		new TypeDiscoverer<Object>(null, null);
	}

	@Test
	public void isNotEqualIfTypesDiffer() {

		TypeDiscoverer<Object> objectTypeInfo = new TypeDiscoverer<Object>(Object.class, EMPTY_MAP);
		TypeDiscoverer<String> stringTypeInfo = new TypeDiscoverer<String>(String.class, EMPTY_MAP);

		assertFalse(objectTypeInfo.equals(stringTypeInfo));
	}

	@Test
	public void isNotEqualIfTypeVariableMapsDiffer() {

		assertFalse(firstMap.equals(secondMap));

		TypeDiscoverer<Object> first = new TypeDiscoverer<Object>(Object.class, firstMap);
		TypeDiscoverer<Object> second = new TypeDiscoverer<Object>(Object.class, secondMap);

		assertFalse(first.equals(second));
	}

	@Test
	public void dealsWithTypesReferencingThemselves() {

		TypeInformation<SelfReferencing> information = from(SelfReferencing.class);
		TypeInformation<?> first = information.getProperty("parent").getMapValueType();
		TypeInformation<?> second = first.getProperty("map").getMapValueType();

		assertEquals(first, second);
	}

	@Test
	public void dealsWithTypesReferencingThemselvesInAMap() {

		TypeInformation<SelfReferencingMap> information = from(SelfReferencingMap.class);
		TypeInformation<?> mapValueType = information.getProperty("map").getMapValueType();

		assertEquals(mapValueType, information);
	}

	@Test
	public void returnsComponentAndValueTypesForMapExtensions() {

		TypeInformation<?> discoverer = new TypeDiscoverer<Object>(CustomMap.class, EMPTY_MAP);

		assertEquals(Locale.class, discoverer.getMapValueType().getType());
		assertEquals(String.class, discoverer.getComponentType().getType());
	}

	@Test
	public void returnsComponentTypeForCollectionExtension() {

		TypeDiscoverer<CustomCollection> discoverer = new TypeDiscoverer<CustomCollection>(CustomCollection.class, firstMap);

		assertEquals(String.class, discoverer.getComponentType().getType());
	}

	@Test
	public void returnsComponentTypeForArrays() {

		TypeDiscoverer<String[]> discoverer = new TypeDiscoverer<String[]>(String[].class, EMPTY_MAP);

		assertEquals(String.class, discoverer.getComponentType().getType());
	}

	@Test // DATACMNS-57
	@SuppressWarnings("rawtypes")
	public void discoveresConstructorParameterTypesCorrectly() throws NoSuchMethodException, SecurityException {

		TypeDiscoverer<GenericConstructors> discoverer = new TypeDiscoverer<GenericConstructors>(GenericConstructors.class,
				firstMap);
		Constructor<GenericConstructors> constructor = GenericConstructors.class.getConstructor(List.class, Locale.class);
		List<TypeInformation<?>> types = discoverer.getParameterTypes(constructor);

		assertThat(types.size(), is(2));
		assertThat(types.get(0).getType(), equalTo((Class) List.class));
		assertThat(types.get(0).getComponentType().getType(), is(equalTo((Class) String.class)));
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void returnsNullForComponentAndValueTypesForRawMaps() {

		TypeDiscoverer<Map> discoverer = new TypeDiscoverer<Map>(Map.class, EMPTY_MAP);

		assertThat(discoverer.getComponentType(), is(nullValue()));
		assertThat(discoverer.getMapValueType(), is(nullValue()));
	}

	@Test // DATACMNS-167
	@SuppressWarnings("rawtypes")
	public void doesNotConsiderTypeImplementingIterableACollection() {

		TypeDiscoverer<Person> discoverer = new TypeDiscoverer<Person>(Person.class, EMPTY_MAP);
		TypeInformation reference = from(Address.class);

		TypeInformation<?> addresses = discoverer.getProperty("addresses");

		assertThat(addresses.isCollectionLike(), is(false));
		assertThat(addresses.getComponentType(), is(reference));

		TypeInformation<?> adressIterable = discoverer.getProperty("addressIterable");

		assertThat(adressIterable.isCollectionLike(), is(true));
		assertThat(adressIterable.getComponentType(), is(reference));
	}

	@Test // GH-3500
	public void superclassFieldsDoNotHideSubclassFields() {

		TypeDiscoverer<TheOne> discoverer = new TypeDiscoverer<TheOne>(TheOne.class, EMPTY_MAP);

		assertEquals(String.class, discoverer.getProperty("theOne").getType());
		assertEquals(String.class, discoverer.getProperty("theOther").getType());
	}

	@Test // GH-3500
	public void doesNotRetainLookupsForUnknownProperties() {

		TypeDiscoverer<Person> discoverer = new TypeDiscoverer<Person>(Person.class, EMPTY_MAP);

		assertThat(discoverer.getProperty("addresses"), is(notNullValue()));

		Map<String, Integer> sizes = getSizesOfMapsHeldBy(discoverer);

		for (int i = 0; i < 10000; i++) {
			assertThat(discoverer.getProperty("unknown" + i), is(nullValue()));
		}

		assertThat(getSizesOfMapsHeldBy(discoverer), is(sizes));
	}

	/**
	 * Returns the sizes of all {@link Map}s held in instance fields of the given object.
	 */
	public static Map<String, Integer> getSizesOfMapsHeldBy(final Object target) {

		final Map<String, Integer> sizes = new HashMap<String, Integer>();

		org.springframework.util.ReflectionUtils.doWithFields(target.getClass(), new FieldCallback() {

			@Override
			public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {

				org.springframework.util.ReflectionUtils.makeAccessible(field);
				Object value = field.get(target);

				if (value instanceof Map) {
					sizes.put(field.getName(), ((Map<?, ?>) value).size());
				}
			}
		}, new FieldFilter() {

			@Override
			public boolean matches(Field field) {
				return !Modifier.isStatic(field.getModifiers());
			}
		});

		return sizes;
	}

	@Test // GH-3500
	public void resolvesPropertiesFromAccessorsAndInterfaces() {

		TypeInformation<AccessorsOnly> type = from(AccessorsOnly.class);

		assertEquals(String.class, type.getProperty("firstname").getType());
		assertEquals(String.class, type.getProperty("Firstname").getType());
		assertEquals(Locale.class, type.getProperty("lastname").getType());
		assertThat(type.getProperty("Unknown"), is(nullValue()));

		TypeInformation<ExtendedNamed> extended = from(ExtendedNamed.class);

		assertEquals(String.class, extended.getProperty("name").getType());
		assertEquals(String.class, extended.getProperty("nickname").getType());
	}

	class Person {

		Addresses addresses;
		Iterable<Address> addressIterable;
	}

	abstract class Addresses implements Iterable<Address> {

	}

	class Address {

	}

	class SelfReferencing {

		Map<String, SelfReferencingMap> parent;
	}

	class SelfReferencingMap {
		Map<String, SelfReferencingMap> map;
	}

	interface CustomMap extends Map<String, Locale> {

	}

	interface CustomCollection extends Collection<String> {

	}

	public static class GenericConstructors {

		public GenericConstructors(List<String> first, Locale second) {

		}
	}

	// GH-3500

	static class SuperclassWithProperties {
		private CharSequence theOne;
		private String theOther;
	}

	class TheOne extends SuperclassWithProperties {
		private String theOne;
	}

	static abstract class AccessorsOnly {

		public abstract String getFirstname();

		public abstract void setLastname(Locale lastname);
	}

	interface Named {
		String getName();
	}

	interface ExtendedNamed extends Named {
		String getNickname();
	}
}
