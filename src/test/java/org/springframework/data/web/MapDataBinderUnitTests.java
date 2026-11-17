/*
 * Copyright 2015-2019 the original author or authors.
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

import static java.util.Collections.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.util.TypeDiscovererUnitTests.getSizesOfMapsHeldBy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.beans.PropertyValues;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.format.support.DefaultFormattingConversionService;

/**
 * Unit tests for {@link MapDataBinder}.
 * 
 * @author Oliver Gierke
 */
public class MapDataBinderUnitTests {

	public @Rule ExpectedException exception = ExpectedException.none();

	@Test // DATACMNS-630
	public void honorsFormattingAnnotationOnAccessor() {

		Date reference = new Date();

		MutablePropertyValues values = new MutablePropertyValues();
		values.add("foo.date", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(reference));

		Map<String, Object> nested = new HashMap<String, Object>();
		nested.put("date", reference);

		assertThat(bind(values), hasEntry("foo", (Object) nested));
	}

	@Test // DATACMNS-630
	public void bindsNestedCollectionElement() {

		MutablePropertyValues values = new MutablePropertyValues();
		values.add("foo.bar.fooBar[0]", "String");

		Map<String, Object> result = bind(values);

		List<String> list = new ArrayList<String>();
		list.add("String");

		assertThat(result, is((Map) singletonMap("foo", singletonMap("bar", singletonMap("fooBar", list)))));
	}

	@Test // DATACMNS-630
	public void bindsNestedPrimitive() {

		MutablePropertyValues values = new MutablePropertyValues();
		values.add("foo.firstname", "Dave");
		values.add("foo.lastname", "Matthews");

		Map<String, Object> result = bind(values);

		Map<String, Object> dave = new HashMap<String, Object>();
		dave.put("firstname", "Dave");
		dave.put("lastname", "Matthews");

		assertThat(result, is((Map) singletonMap("foo", dave)));
	}

	@Test // DATACMNS-630
	public void skipsPropertyNotExposedByTheTypeHierarchy() {

		MutablePropertyValues values = new MutablePropertyValues();
		values.add("somethingWeird", "Value");

		assertThat(bind(values), is(Collections.<String, Object> emptyMap()));
	}

	@Test // GH-3500
	public void doesNotRetainUnknownPropertyNames() {

		TypeInformation<Root> type = ClassTypeInformation.from(Root.class);

		MutablePropertyValues values = new MutablePropertyValues();
		values.add("foo.firstname", "Dave");

		assertThat(bind(values).isEmpty(), is(false));

		Map<String, Integer> sizes = getSizesOfMapsHeldBy(type);

		for (int i = 0; i < 1000; i++) {

			MutablePropertyValues unknown = new MutablePropertyValues();
			unknown.add("unknownProperty" + i, "Value");

			assertThat(bind(unknown), is(Collections.<String, Object> emptyMap()));
		}

		assertThat(getSizesOfMapsHeldBy(type), is(sizes));
	}

	@Test // DATACMNS-1264
	public void dropsMapExpressionsForCollectionReferences() {

		ConfigurablePropertyAccessor accessor = new MapDataBinder(Bar.class, new DefaultFormattingConversionService())
				.getPropertyAccessor();

		exception.expect(NotWritablePropertyException.class);
		exception.expectCause(is(Matchers.<Throwable> instanceOf(SpelEvaluationException.class)));

		accessor.setPropertyValue("fooBar['foo']", null);
	}

	@Test // DATACMNS-1264
	public void rejectsExpressionContainingTypeExpression() {

		ConfigurablePropertyAccessor accessor = new MapDataBinder(Bar.class, new DefaultFormattingConversionService())
				.getPropertyAccessor();

		exception.expect(NotWritablePropertyException.class);
		exception.expectCause(is(Matchers.<Throwable> instanceOf(SpelEvaluationException.class)));

		accessor.setPropertyValue("fooBar[T(java.lang.String)]", null);
	}

	@Test // GH-3495
	public void acceptsValuesWithinConfiguredMaxCollectionSize() {

		MapDataBinder dataBinder = new MapDataBinder(Bar.class, new DefaultFormattingConversionService());
		dataBinder.setAutoGrowCollectionLimit(300);
		ConfigurablePropertyAccessor accessor = dataBinder.getPropertyAccessor();

		accessor.setPropertyValue("fooBar[299]", "Spring");

		assertThat((Collection<?>) dataBinder.getTarget().get("fooBar"), hasSize(300));
	}

	@Test // GH-3495
	public void rejectsValuesBeyondCustomizedMaxCollectionSize() {

		MapDataBinder dataBinder = new MapDataBinder(Bar.class, new DefaultFormattingConversionService());
		dataBinder.setAutoGrowCollectionLimit(300);
		ConfigurablePropertyAccessor accessor = dataBinder.getPropertyAccessor();

		exception.expect(NotWritablePropertyException.class);
		exception.expectCause(is(Matchers.<Throwable> instanceOf(SpelEvaluationException.class)));

		accessor.setPropertyValue("fooBar[300]", "Spring");
	}

	@Test // GH-3495
	public void rejectsValuesBeyondConfiguredMaxCollectionSize() {

		newAccessor().setPropertyValue("fooBar[299]", "Spring");

		exception.expect(NotWritablePropertyException.class);
		exception.expectCause(is(Matchers.<Throwable> instanceOf(SpelEvaluationException.class)));

		newAccessor().setPropertyValue("fooBar[2000]", "Spring");
	}

	@Test // GH-3495
	public void rejectsAssignmentsWithinPropertyPath() {

		MapDataBinder dataBinder = new MapDataBinder(Bar.class, new DefaultFormattingConversionService());
		ConfigurablePropertyAccessor accessor = dataBinder.getPropertyAccessor();

		try {
			accessor.setPropertyValue("fooBar[fooBar='Spring']", "Data");
			fail("Expected NotWritablePropertyException!");
		} catch (NotWritablePropertyException o_O) {
			assertThat(o_O.getCause(), is(Matchers.<Throwable> instanceOf(SpelEvaluationException.class)));
		}

		assertThat(dataBinder.getTarget(), not(hasEntry("fooBar", (Object) "Spring")));
	}

	/**
	 * A collection is only ever grown on the first write to a property, so every assertion about the limit needs to start
	 * from a fresh, empty target.
	 */
	private static ConfigurablePropertyAccessor newAccessor() {
		return new MapDataBinder(Bar.class, new DefaultFormattingConversionService()).getPropertyAccessor();
	}

	private static Map<String, Object> bind(PropertyValues values) {

		MapDataBinder binder = new MapDataBinder(Root.class, new DefaultFormattingConversionService());
		binder.bind(values);

		return binder.getTarget();
	}

	interface Root {

		Foo getFoo();

		Bar getBar();
	}

	interface Foo {

		Bar getBar();

		String getLastname();

		String getFirstname();

		@DateTimeFormat(iso = ISO.DATE_TIME)
		Date getDate();
	}

	interface Bar {
		Collection<String> getFooBar();
	}
}
