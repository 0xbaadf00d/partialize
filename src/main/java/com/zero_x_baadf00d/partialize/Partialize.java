/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thibault Meyer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.zero_x_baadf00d.partialize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zero_x_baadf00d.partialize.annotation.PartialField;
import com.zero_x_baadf00d.partialize.annotation.PartialFields;
import com.zero_x_baadf00d.partialize.converter.Converter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Create a partial JSON document from any kind of objects.
 *
 * @author Thibault Meyer
 * @version 16.01
 * @since 16.01
 */
public class Partialize {

    /**
     * Default maximum reachable depth level.
     *
     * @since 16.01
     */
    public static final int DEFAULT_MAXIMUM_DEPTH = 64;

    /**
     * Default scanner delimiter pattern.
     *
     * @since 16.01
     */
    private static final String SCANNER_DELIMITER = ",";

    /**
     * Pattern used to extract arguments.
     *
     * @since 16.01
     */
    private final Pattern fieldArgsPattern = Pattern.compile("([a-zA-Z0-9]{1,})\\((.+)\\)");

    /**
     * Object mapper used to create new object nodes.
     *
     * @since 16.01
     */
    private final ObjectMapper objectMapper;

    /**
     * The maximum reachable depth level.
     *
     * @since 16.01
     */
    private final int maximumDepth;

    /**
     * Build a default instance.
     *
     * @since 16.01
     */
    public Partialize() {
        this(Partialize.DEFAULT_MAXIMUM_DEPTH);
    }

    /**
     * Build an instance with a specific maximum depth value set.
     *
     * @param maximumDepth Maximum allowed depth value to set
     * @since 16.01
     */
    public Partialize(final int maximumDepth) {
        this.objectMapper = new ObjectMapper();
        this.maximumDepth = maximumDepth > 0 ? maximumDepth : 1;
    }

    /**
     * Build a JSON object from data taken from the scanner and
     * the given class type and instance.
     *
     * @param fields   The field query to request
     * @param clazz    The class of the object to render
     * @param instance The instance of the object to render
     * @return An instance of {@code ContainerNode}
     * @see ContainerNode
     * @since 16.01
     */
    public ContainerNode buildPartialObject(final String fields, final Class<?> clazz, final Object instance) {
        if (instance instanceof Collection<?>) {
            final ArrayNode partialArray = this.objectMapper.createArrayNode();
            if (((Collection<?>) instance).size() > 0) {
                for (final Object o : (Collection<?>) instance) {
                    partialArray.add(this.buildPartialObject(0, fields, o.getClass(), o));
                }
            }
            return partialArray;
        } else {
            return this.buildPartialObject(0, fields, clazz, instance);
        }
    }

    /**
     * Add requested item on the partial JSON document.
     *
     * @param depth        Current depth level
     * @param field        The field name
     * @param args         The field Arguments
     * @param partialArray The current partial JSON document part
     * @param clazz        The class of the object to add
     * @param object       The object to add
     * @since 16.01
     */
    private void internalBuild(final int depth, final String field, final String args, final ArrayNode partialArray, final Class<?> clazz, final Object object) {
        if (object == null) {
            partialArray.addNull();
        } else if (object instanceof String) {
            partialArray.add((String) object);
        } else if (object instanceof Integer) {
            partialArray.add((Integer) object);
        } else if (object instanceof Long) {
            partialArray.add((Long) object);
        } else if (object instanceof Double) {
            partialArray.add((Double) object);
        } else if (object instanceof UUID) {
            partialArray.add(object.toString());
        } else if (object instanceof Boolean) {
            partialArray.add((Boolean) object);
        } else if (object instanceof Collection<?>) {
            final ArrayNode anotherPartialArray = partialArray.addArray();
            if (((Collection<?>) object).size() > 0) {
                for (final Object o : (Collection<?>) object) {
                    this.internalBuild(depth, field, args, anotherPartialArray, o.getClass(), o);
                }
            }
        } else if (object instanceof Enum) {
            final String tmp = object.toString();
            try {
                partialArray.add(Integer.valueOf(tmp));
            } catch (NumberFormatException ignore) {
                partialArray.add(tmp);
            }
        } else {
            try {
                if (clazz.getDeclaredField(field).isAnnotationPresent(PartialField.class)) {
                    final Class<?> convertClazz = clazz.getDeclaredField(field).getAnnotation(PartialField.class).value();
                    try {
                        final Converter converter = (Converter) convertClazz.newInstance();
                        converter.convert(field, object, partialArray);
                    } catch (InstantiationException ex) {
                        ex.printStackTrace();
                        partialArray.add(object.toString());
                    }
                } else {
                    partialArray.add(this.buildPartialObject(depth + 1, args, object.getClass(), object));
                }
            } catch (NoSuchFieldException | IllegalAccessException ignore) {
                partialArray.add(this.buildPartialObject(depth + 1, args, object.getClass(), object));
            }
        }
    }

    /**
     * Add requested item on the partial JSON document.
     *
     * @param depth         Current depth level
     * @param field         The field name
     * @param args          The field Arguments
     * @param partialObject The current partial JSON document part
     * @param clazz         The class of the object to add
     * @param object        The object to add
     * @since 16.01
     */
    private void internalBuild(final int depth, final String field, final String args, final ObjectNode partialObject, final Class<?> clazz, final Object object) {
        if (object == null) {
            partialObject.putNull(field);
        } else if (object instanceof String) {
            partialObject.put(field, (String) object);
        } else if (object instanceof Integer) {
            partialObject.put(field, (Integer) object);
        } else if (object instanceof Long) {
            partialObject.put(field, (Long) object);
        } else if (object instanceof Double) {
            partialObject.put(field, (Double) object);
        } else if (object instanceof UUID) {
            partialObject.put(field, object.toString());
        } else if (object instanceof Boolean) {
            partialObject.put(field, (Boolean) object);
        } else if (object instanceof Collection<?>) {
            final ArrayNode partialArray = partialObject.putArray(field);
            if (((Collection<?>) object).size() > 0) {
                for (final Object o : (Collection<?>) object) {
                    this.internalBuild(depth, field, args, partialArray, o.getClass(), o);
                }
            }
        } else if (object instanceof Map<?, ?>) {
            this.buildPartialObject(depth + 1, args, object.getClass(), object, partialObject.putObject(field));
        } else if (object instanceof Enum) {
            final String tmp = object.toString();
            try {
                partialObject.put(field, Integer.valueOf(tmp));
            } catch (NumberFormatException ignore) {
                partialObject.put(field, tmp);
            }
        } else {
            try {
                if (clazz.getDeclaredField(field).isAnnotationPresent(PartialField.class)) {
                    final Class<?> convertClazz = clazz.getDeclaredField(field).getAnnotation(PartialField.class).value();
                    try {
                        final Converter converter = (Converter) convertClazz.newInstance();
                        converter.convert(field, object, partialObject);
                    } catch (InstantiationException ex) {
                        ex.printStackTrace();
                        partialObject.put(field, object.toString());
                    }
                } else {
                    this.buildPartialObject(depth + 1, args, object.getClass(), object, partialObject.putObject(field));
                }
            } catch (NoSuchFieldException | IllegalAccessException ignore) {
                this.buildPartialObject(depth + 1, args, object.getClass(), object, partialObject.putObject(field));
            }
        }
    }

    /**
     * Build a JSON object from data taken from the scanner and
     * the given class type and instance.
     *
     * @param depth    The current depth
     * @param fields   The field names to requests
     * @param clazz    The class of the object to render
     * @param instance The instance of the object to render
     * @return A JSON Object
     * @since 16.01
     */
    private ObjectNode buildPartialObject(final int depth, final String fields, final Class<?> clazz, final Object instance) {
        return this.buildPartialObject(depth, fields, clazz, instance, this.objectMapper.createObjectNode());
    }

    /**
     * Build a JSON object from data taken from the scanner and
     * the given class type and instance.
     *
     * @param depth         The current depth
     * @param fields        The field names to requests
     * @param clazz         The class of the object to render
     * @param instance      The instance of the object to render
     * @param partialObject The partial JSON document
     * @return A JSON Object
     * @since 16.01
     */
    private ObjectNode buildPartialObject(final int depth, String fields, final Class<?> clazz, final Object instance, final ObjectNode partialObject) {
        if (depth <= this.maximumDepth) {
            if (clazz.isAnnotationPresent(PartialFields.class)) {
                List<String> allowedFields = Arrays.asList(instance.getClass().getAnnotation(PartialFields.class).allowedFields());
                List<String> wildCardFields = Arrays.asList(instance.getClass().getAnnotation(PartialFields.class).wildcardFields());
                if (allowedFields.isEmpty()) {
                    allowedFields = new ArrayList<>();
                    for (final Method m : clazz.getDeclaredMethods()) {
                        final String methodName = m.getName();
                        if (methodName.startsWith("get")) {
                            final char[] c = methodName.substring(3).toCharArray();
                            c[0] = Character.toLowerCase(c[0]);
                            allowedFields.add(new String(c));
                        }
                    }
                }
                if (wildCardFields.isEmpty()) {
                    wildCardFields = allowedFields;
                }
                if (fields == null || fields.length() == 0) {
                    fields = wildCardFields.stream().collect(Collectors.joining(","));
                }

                Scanner scanner = new Scanner(fields);
                scanner.useDelimiter(Partialize.SCANNER_DELIMITER);
                while (scanner.hasNext()) {
                    String word = scanner.next();
                    String args = null;
                    if (word.compareTo("*") == 0) {
                        scanner.close();
                        scanner = new Scanner(wildCardFields.stream().collect(Collectors.joining(",")));
                        scanner.useDelimiter(Partialize.SCANNER_DELIMITER);
                    }
                    if (word.contains("(")) {
                        while (scanner.hasNext() && (StringUtils.countMatches(word, "(") != StringUtils.countMatches(word, ")"))) {
                            word += "," + scanner.next();
                        }
                        final Matcher m = this.fieldArgsPattern.matcher(word);
                        if (m.find()) {
                            word = m.group(1);
                            args = m.group(2);
                        }
                    }
                    final String field = word;
                    if (allowedFields.stream().anyMatch(f -> f.toLowerCase(Locale.ENGLISH).compareTo(field.toLowerCase(Locale.ENGLISH)) == 0)) {
                        try {
                            final Method method = clazz.getMethod("get" + WordUtils.capitalize(field));
                            final Object object = method.invoke(instance);
                            this.internalBuild(depth, field, args, partialObject, clazz, object);
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignore) {
                        }
                    }
                }
                return partialObject;
            } else if (instance instanceof Map<?, ?>) {
                if (fields == null || fields.isEmpty()) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) instance).entrySet()) {
                        this.internalBuild(depth, String.valueOf(e.getKey()), null, partialObject, e.getValue() == null ? Object.class : e.getValue().getClass(), e.getValue());
                    }
                } else {
                    final Map<?, ?> tmpMap = (Map<?, ?>) instance;
                    for (final String k : fields.split(",")) {
                        final Object o = tmpMap.get(k);
                        this.internalBuild(depth, k, null, partialObject, o == null ? Object.class : o.getClass(), o);
                    }
                }
            } else {
                throw new RuntimeException(clazz.getCanonicalName() + " is not annotated");
            }
        }
        return partialObject;
    }
}
