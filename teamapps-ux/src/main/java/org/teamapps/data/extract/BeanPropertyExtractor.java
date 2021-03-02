/*-
 * ========================LICENSE_START=================================
 * TeamApps
 * ---
 * Copyright (C) 2014 - 2021 TeamApps.org
 * ---
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
 * =========================LICENSE_END==================================
 */
package org.teamapps.data.extract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teamapps.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BeanPropertyExtractor<RECORD> implements PropertyExtractor<RECORD> {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeanPropertyExtractor.class);
	private static final Map<ClassAndPropertyName, ValueExtractor> gettersByClassAndPropertyName = new ConcurrentHashMap<>();

	private final Map<String, ValueExtractor<RECORD>> customExtractors = new HashMap<>(0);

	@Override
	public Object getValue(RECORD record, String propertyName) {
		ValueExtractor<RECORD> valueExtractor = getValueExtractor(record.getClass(), propertyName);
		return valueExtractor.extract(record);
	}

	protected ValueExtractor<RECORD> getValueExtractor(Class clazz, String propertyName) {
		ValueExtractor<RECORD> valueExtractor = customExtractors.get(propertyName);
		if (valueExtractor != null) {
			return valueExtractor;
		} else {
			return gettersByClassAndPropertyName.computeIfAbsent(
					new ClassAndPropertyName(clazz, propertyName),
					classAndPropertyName -> createValueExtractor(classAndPropertyName)
			);
		}
	}

	private ValueExtractor<RECORD> createValueExtractor(ClassAndPropertyName classAndPropertyName) {
		Method getter = ReflectionUtil.findGetter(classAndPropertyName.clazz, classAndPropertyName.propertyName);
		Field field = ReflectionUtil.findField(classAndPropertyName.clazz, classAndPropertyName.propertyName);
		return (record) -> {
			if (getter != null) {
				return ReflectionUtil.invokeMethod(record, getter);
			} else {
				if (field != null) {
					try {
						return field.get(record);
					} catch (IllegalAccessException ex) {
						LOGGER.debug("Could not access field for property {} on class {}!", classAndPropertyName.propertyName, record.getClass().getCanonicalName());
					}
				} else {
					LOGGER.debug("Could not find getter or field for property {} on class {}!", classAndPropertyName.propertyName, record.getClass().getCanonicalName());
				}
			}
			return null;
		};
	}

	public BeanPropertyExtractor<RECORD> addProperty(String propertyName, ValueExtractor<RECORD> valueExtractor) {
		this.customExtractors.put(propertyName, valueExtractor);
		return this;
	}

}
