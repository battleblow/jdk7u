/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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


#ifndef MLIB_IMAGE_H
#define MLIB_IMAGE_H

#ifdef __OpenBSD__
#include <sys/types.h>
#endif

#ifdef _ALLBSD_SOURCE
#include <machine/endian.h>

/* BSD's always define both _LITTLE_ENDIAN && _BIG_ENDIAN */
#if defined(_LITTLE_ENDIAN) && defined(_BIG_ENDIAN) && \
    _BYTE_ORDER == _BIG_ENDIAN
#undef _LITTLE_ENDIAN
#endif

#endif /* _ALLBSD_SOURCE */

#include <mlib_types.h>
#include <mlib_status.h>
#include <mlib_sys.h>
#include <mlib_image_types.h>
#include <mlib_image_proto.h>
#include <mlib_image_blend_proto.h>
#include <mlib_image_get.h>

#endif  /* MLIB_IMAGE_H */
