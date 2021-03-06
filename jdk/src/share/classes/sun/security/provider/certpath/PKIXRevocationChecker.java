/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.provider.certpath;

import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Extension;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A {@code PKIXCertPathChecker} for checking the revocation status of
 * certificates with the PKIX algorithm.
 *
 * <p>A {@code PKIXRevocationChecker} checks the revocation status of
 * certificates with the Online Certificate Status Protocol (OCSP) or
 * Certificate Revocation Lists (CRLs). OCSP is described in RFC 2560 and
 * is a network protocol for determining the status of a certificate. A CRL
 * is a time-stamped list identifying revoked certificates, and RFC 5280
 * describes an algorithm for determining the revocation status of certificates
 * using CRLs.
 *
 * <p>Each {@code PKIXRevocationChecker} must be able to check the revocation
 * status of certificates with OCSP and CRLs. By default, OCSP is the
 * preferred mechanism for checking revocation status, with CRLs as the
 * fallback mechanism. However, this preference can be switched to CRLs with
 * the {@link Option.PREFER_CRLS} option.
 *
 * <p>A {@code PKIXRevocationChecker} is obtained by calling the
 * {@link CertPathValidator#getRevocationChecker getRevocationChecker} method
 * of a PKIX {@code CertPathValidator}. Additional parameters and options
 * specific to revocation can be set (by calling {@link #setOCSPResponder}
 * method for instance). The {@code PKIXRevocationChecker} is added to
 * a {@code PKIXParameters} object using the
 * {@link PKIXParameters#addCertPathChecker addCertPathChecker}
 * or {@link PKIXParameters#setCertPathCheckers setCertPathCheckers} method,
 * and then the {@code PKIXParameters} is passed along with the {@code CertPath}
 * to be validated to the {@link CertPathValidator#validate validate} method
 * of a PKIX {@code CertPathValidator}. When supplying a revocation checker in
 * this manner, do not enable the default revocation checking mechanism (by
 * calling {@link PKIXParameters#setRevocationEnabled}.
 *
 * <p>Note that when a {@code PKIXRevocationChecker} is added to
 * {@code PKIXParameters}, it clones the {@code PKIXRevocationChecker};
 * thus any subsequent modifications to the {@code PKIXRevocationChecker}
 * have no effect.
 *
 * <p>Any parameter that is not set (or is set to {@code null}) will be set to
 * the default value for that parameter.
 *
 * <p><b>Concurrent Access</b>
 *
 * <p>Unless otherwise specified, the methods defined in this class are not
 * thread-safe. Multiple threads that need to access a single object
 * concurrently should synchronize amongst themselves and provide the
 * necessary locking. Multiple threads each manipulating separate objects
 * need not synchronize.
 *
 * @since 1.8
 */
public abstract class PKIXRevocationChecker extends PKIXCertPathChecker
  implements CertPathChecker {
    private URI ocspResponder;
    private X509Certificate ocspResponderCert;
    private List<Extension> ocspExtensions = Collections.<Extension>emptyList();
    private Map<X509Certificate, byte[]> ocspStapled = Collections.emptyMap();
    private Set<Option> options = Collections.emptySet();

    protected PKIXRevocationChecker() {}

    /**
     * Sets the URI that identifies the location of the OCSP responder. This
     * overrides the {@code ocsp.responderURL} security property and any
     * responder specified in a certificate's Authority Information Access
     * Extension, as defined in RFC 5280.
     *
     * @param uri the responder URI
     */
    public void setOCSPResponder(URI uri) {
        this.ocspResponder = uri;
    }

    /**
     * Gets the URI that identifies the location of the OCSP responder. This
     * overrides the {@code ocsp.responderURL} security property. If this
     * parameter or the {@code ocsp.responderURL} property is not set, the
     * location is determined from the certificate's Authority Information
     * Access Extension, as defined in RFC 5280.
     *
     * @return the responder URI, or {@code null} if not set
     */
    public URI getOCSPResponder() {
        return ocspResponder;
    }

    /**
     * Sets the OCSP responder's certificate. This overrides the
     * {@code ocsp.responderCertSubjectName},
     * {@code ocsp.responderCertIssuerName},
     * and {@code ocsp.responderCertSerialNumber} security properties.
     *
     * @param cert the responder's certificate
     */
    public void setOCSPResponderCert(X509Certificate cert) {
        this.ocspResponderCert = cert;
    }

    /**
     * Gets the OCSP responder's certificate. This overrides the
     * {@code ocsp.responderCertSubjectName},
     * {@code ocsp.responderCertIssuerName},
     * and {@code ocsp.responderCertSerialNumber} security properties. If this
     * parameter or the aforementioned properties are not set, then the
     * responder's certificate is determined as specified in RFC 2560.
     *
     * @return the responder's certificate, or {@code null} if not set
     */
    public X509Certificate getOCSPResponderCert() {
        return ocspResponderCert;
    }

    // request extensions; single extensions not supported
    /**
     * Sets the optional OCSP request extensions.
     *
     * @param extensions a list of extensions. The list is copied to protect
     *        against subsequent modification.
     */
    public void setOCSPExtensions(List<Extension> extensions)
    {
        this.ocspExtensions = (extensions == null)
                              ? Collections.<Extension>emptyList()
                              : new ArrayList<Extension>(extensions);
    }

    /**
     * Gets the optional OCSP request extensions.
     *
     * @return an unmodifiable list of extensions. Returns an empty list if no
     *         extensions have been specified.
     */
    public List<Extension> getOCSPExtensions() {
        return Collections.unmodifiableList(ocspExtensions);
    }

    /**
     * Sets the stapled OCSP responses. These responses are used to determine
     * the revocation status of the specified certificates when OCSP is used.
     *
     * @param responses a map of stapled OCSP responses. Each key is an
     *        {@code X509Certificate} that maps to the corresponding
     *        DER-encoded OCSP response for that certificate. A deep copy of
     *        the map is performed to protect against subsequent modification.
     */
    public void setOCSPStapledResponses(Map<X509Certificate, byte[]> responses)
    {
        if (responses == null) {
            this.ocspStapled = Collections.<X509Certificate, byte[]>emptyMap();
        } else {
            Map<X509Certificate, byte[]> copy = new HashMap<>(responses.size());
            for (Map.Entry<X509Certificate, byte[]> e : responses.entrySet()) {
                copy.put(e.getKey(), e.getValue().clone());
            }
            this.ocspStapled = copy;
        }
    }

    /**
     * Gets the stapled OCSP responses. These responses are used to determine
     * the revocation status of the specified certificates when OCSP is used.
     *
     * @return a map of stapled OCSP responses. Each key is an
     *        {@code X509Certificate} that maps to the corresponding
     *        DER-encoded OCSP response for that certificate. A deep copy of
     *        the map is returned to protect against subsequent modification.
     *        Returns an empty map if no responses have been specified.
     */
    public Map<X509Certificate, byte[]> getOCSPStapledResponses() {
        Map<X509Certificate, byte[]> copy = new HashMap<>(ocspStapled.size());
        for (Map.Entry<X509Certificate, byte[]> e : ocspStapled.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        return copy;
    }

    /**
     * Sets the revocation options.
     *
     * @param options a set of revocation options. The set is copied to protect
     *        against subsequent modification.
     */
    public void setOptions(Set<Option> options) {
        this.options = (options == null)
                       ? Collections.<Option>emptySet()
                       : new HashSet<Option>(options);
    }

    /**
     * Gets the revocation options.
     *
     * @return an unmodifiable set of revocation options, or an empty set if
     *         none are specified
     */
    public Set<Option> getOptions() {
        return Collections.unmodifiableSet(options);
    }

    @Override
    public Object clone() {
        PKIXRevocationChecker copy = (PKIXRevocationChecker)super.clone();
        copy.ocspExtensions = new ArrayList<>(ocspExtensions);
        copy.ocspStapled = new HashMap<>(ocspStapled);
        // deep-copy the encoded stapled responses, since they are mutable
        for (Map.Entry<X509Certificate, byte[]> entry :
                 copy.ocspStapled.entrySet())
        {
            byte[] encoded = entry.getValue();
            entry.setValue(encoded.clone());
        }
        copy.options = new HashSet<>(options);
        return copy;
    }

    /**
     * Various revocation options that can be specified for the revocation
     * checking mechanism.
     */
    public enum Option {
        /**
         * Only check the revocation status of end-entity certificates.
         */
        ONLY_END_ENTITY,
        /**
         * Prefer CRLs to OSCP. The default behavior is to prefer OCSP. Each
         * PKIX implementation should document further details of their
         * specific preference rules and fallback policies.
         */
        PREFER_CRLS,
        /**
         * Ignore network failures. The default behavior is to consider it a
         * failure if the revocation status of a certificate cannot be obtained
         * due to a network error. This option applies to both OCSP and CRLs.
         */
        SOFT_FAIL
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation calls
     * {@code check(cert, java.util.Collections.<String>emptySet())}.
     */
    @Override
    public void check(Certificate cert) throws CertPathValidatorException {
        check(cert, java.util.Collections.<String>emptySet());
    }
}
