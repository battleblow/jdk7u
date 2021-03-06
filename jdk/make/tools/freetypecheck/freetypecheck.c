/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

/* Test program for freetype sanity check.
   Prints "Failed" messages to STDOUT if check fails. */

#include <stdio.h>
#include <string.h>

#include "ft2build.h"
#include FT_FREETYPE_H

#ifdef _MSC_VER
#if _MSC_VER > 1400 && _MSC_VER < 1600

/*
 * When building for Microsoft Windows, your program has a dependency
 * on msvcr??.dll.
 *
 * When using Visual Studio 2005 or later, that must be recorded in
 * the <program>.exe.manifest file.
 *
 * Reference:
 *     C:/Program Files/Microsoft SDKs/Windows/v6.1/include/crtdefs.h
 */
#include <crtassem.h>
#ifdef _M_IX86

#pragma comment(linker,"/manifestdependency:\"type='win32' "            \
        "name='" __LIBRARIES_ASSEMBLY_NAME_PREFIX ".CRT' "              \
        "version='" _CRT_ASSEMBLY_VERSION "' "                          \
        "processorArchitecture='x86' "                                  \
        "publicKeyToken='" _VC_ASSEMBLY_PUBLICKEYTOKEN "'\"")

#endif /* _M_IX86 */

//This may not be necessary yet for the Windows 64-bit build, but it
//will be when that build environment is updated.  Need to test to see
//if it is harmless:
#ifdef _M_AMD64

#pragma comment(linker,"/manifestdependency:\"type='win32' "            \
        "name='" __LIBRARIES_ASSEMBLY_NAME_PREFIX ".CRT' "              \
        "version='" _CRT_ASSEMBLY_VERSION "' "                          \
        "processorArchitecture='amd64' "                                \
        "publicKeyToken='" _VC_ASSEMBLY_PUBLICKEYTOKEN "'\"")

#endif  /* _M_AMD64 */
#endif  /* _MSC_VER > 1400 && _MSC_VER < 1600 */
#endif  /* _MSC_VER */

#define QUOTEMACRO(x) QUOTEME(x)
#define QUOTEME(x) #x

int compare_versions(FT_Int req_major, FT_Int req_minor, FT_Int req_patch,
                     FT_Int major, FT_Int minor, FT_Int patch) {
    if (major > req_major) {
        printf("Major version %d greater than required major version %d\n",
               major, req_major);
        return 0;
    }
    if (major < req_major) {
        printf("Major version %d less than required major version %d\n",
               major, req_major);
        return -1;
    }
    printf("Major version %d equal to required major version %d\n",
           major, req_major);
    if (minor > req_minor) {
        printf("Minor version %d greater than required minor version %d\n",
               minor, req_minor);
        return 0;
    }
    if (minor < req_minor) {
        printf("Minor version %d less than required minor version %d\n",
               minor, req_minor);
        return -2;
    }
    printf("Minor version %d equal to required minor version %d\n",
           minor, req_minor);
    if (patch >= req_patch) {
        printf("Patch version %d greater than or equal to required patch version %d\n",
               patch, req_patch);
        return 0;
    }
    printf("Patch version %d less than required patch version %d\n",
           patch, req_patch);
    return -3;
}

int main(int argc, char** argv) {
   FT_Int major, minor, patch, req_major, req_minor, req_patch;
   FT_Library library;

   sscanf(QUOTEMACRO(REQUIRED_FREETYPE_VERSION),
          "%d.%d.%d", &req_major, &req_minor, &req_patch);
   printf("Required version of freetype: %d.%d.%d\n",
          req_major, req_minor, req_patch);

   printf("Detected freetype headers: %d.%d.%d\n",
          FREETYPE_MAJOR, FREETYPE_MINOR, FREETYPE_PATCH);
   if (compare_versions(req_major, req_minor, req_patch,
                        FREETYPE_MAJOR, FREETYPE_MINOR, FREETYPE_PATCH) < 0) {
       printf("Failed: headers are too old.\n");
   }

   FT_Init_FreeType(&library);
   FT_Library_Version(library, &major, &minor, &patch);

   printf("Detected freetype library: %d.%d.%d\n",
          major, minor, patch);
   if (compare_versions(req_major, req_minor, req_patch,
                        major, minor, patch) < 0) {
      printf("Failed: too old library.\n");
   }

   return 0;
}
