/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.jcp.xml.dsig.internal.dom;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import sun.security.util.Parsing;

/**
 * The secure validation policy as specified by the
 * jdk.xml.dsig.secureValidationPolicy security property.
 */
public final class Policy {

    // all restrictions are initialized to be unconstrained
    private static Set<URI> disallowedAlgs = new HashSet<>();
    private static int maxTrans = Integer.MAX_VALUE;
    private static int maxRefs = Integer.MAX_VALUE;
    private static Set<String> disallowedRefUriSchemes = new HashSet<>();
    private static Map<String, Integer> minKeyMap = new HashMap<>();
    private static boolean noDuplicateIds = false;
    private static boolean noRMLoops = false;

    static {
        try {
            initialize();
        } catch (Exception e) {
            throw new SecurityException(
                "Cannot initialize the secure validation policy", e);
        }
    }

    private Policy() {}

    private static void initialize() {
        String prop =
            AccessController.doPrivileged(new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return Security.getProperty("jdk.xml.dsig.secureValidationPolicy");
                    }
                });
        if (prop == null || prop.isEmpty()) {
            // no policy specified, so don't enforce any restrictions
            return;
        }
        String[] entries = prop.split(",");
        for (String entry : entries) {
            String[] tokens = entry.split("\\s");
            String type = tokens[0];
            switch(type) {
                case "disallowAlg":
                    if (tokens.length != 2) {
                        error(entry);
                    }
                    disallowedAlgs.add(URI.create(tokens[1]));
                    break;
                case "maxTransforms":
                    if (tokens.length != 2) {
                        error(entry);
                    }
                    maxTrans = Parsing.parseUnsignedInt(tokens[1]);
                    break;
                case "maxReferences":
                    if (tokens.length != 2) {
                        error(entry);
                    }
                    maxRefs = Parsing.parseUnsignedInt(tokens[1]);
                    break;
                case "disallowReferenceUriSchemes":
                    if (tokens.length == 1) {
                        error(entry);
                    }
                    for (int i = 1; i < tokens.length; i++) {
                        String scheme = tokens[i];
                        disallowedRefUriSchemes.add(
                            scheme.toLowerCase(Locale.ROOT));
                    }
                    break;
                case "minKeySize":
                    if (tokens.length != 3) {
                        error(entry);
                    }
                    minKeyMap.put(tokens[1],
                                  Parsing.parseUnsignedInt(tokens[2]));
                    break;
                case "noDuplicateIds":
                    if (tokens.length != 1) {
                        error(entry);
                    }
                    noDuplicateIds = true;
                    break;
                case "noRetrievalMethodLoops":
                    if (tokens.length != 1) {
                        error(entry);
                    }
                    noRMLoops = true;
                    break;
                default:
                    error(entry);
            }
        }
    }

    public static boolean restrictAlg(String alg) {
        try {
            URI uri = new URI(alg);
            return disallowedAlgs.contains(uri);
        } catch (URISyntaxException use) {
            return false;
        }
    }

    public static boolean restrictNumTransforms(int numTrans) {
        return (numTrans > maxTrans);
    }

    public static boolean restrictNumReferences(int numRefs) {
        return (numRefs > maxRefs);
    }

    public static boolean restrictReferenceUriScheme(String uri) {
        if (uri != null) {
            String scheme = java.net.URI.create(uri).getScheme();
            if (scheme != null) {
                return disallowedRefUriSchemes.contains(
                    scheme.toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }

    public static boolean restrictKey(String type, int size) {
        Integer typeMin = minKeyMap.get(type);
        if (typeMin == null) {
            return size < 0;
        } else {
            return size < typeMin.intValue();
        }
    }

    public static boolean restrictDuplicateIds() {
        return noDuplicateIds;
    }

    public static boolean restrictRetrievalMethodLoops() {
        return noRMLoops;
    }

    public static Set<URI> disabledAlgs() {
        return Collections.<URI>unmodifiableSet(disallowedAlgs);
    }

    public static int maxTransforms() {
        return maxTrans;
    }

    public static int maxReferences() {
        return maxRefs;
    }

    public static Set<String> disabledReferenceUriSchemes() {
        return Collections.<String>unmodifiableSet(disallowedRefUriSchemes);
    }

    public static int minKeySize(String type) {
        Integer typeMin = minKeyMap.get(type);
        if (typeMin == null) {
            return 0;
        } else {
            return typeMin.intValue();
        }
    }

    private static void error(String entry) {
        throw new IllegalArgumentException(
            "Invalid jdk.xml.dsig.secureValidationPolicy entry: " + entry);
    }
}