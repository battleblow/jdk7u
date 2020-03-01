/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#include "precompiled.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/vmError.hpp"

#include <sys/types.h>
#include <sys/wait.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <signal.h>
#ifdef __FreeBSD__
#include <limits.h>
#include <sys/sysctl.h>
#endif

#define GDB_CMD "gdb"

static void set_debugger(char *buf, int buflen) {
  int pid = os::current_process_id();
#ifdef __FreeBSD__
  char cmd[PATH_MAX+1];
  int name[4] = { CTL_KERN, KERN_PROC, KERN_PROC_PATHNAME, pid };
  size_t len = sizeof(cmd);
  if (sysctl(name, 4, cmd, &len, NULL, 0) == 0 && len > 0) {
    cmd[len] = '\0';
    jio_snprintf(buf, buflen, "%s %s %d", GDB_CMD, cmd, pid);
  } else
#endif
  jio_snprintf(buf, buflen, "%s /proc/%d/file %d", GDB_CMD, pid, pid);
}

void VMError::show_message_box(char *buf, int buflen) {
  bool yes;
  do {
    intx tid = os::current_thread_id();
    set_debugger(buf, buflen);
    int len = (int)strlen(buf) + 1;
    char *msg = &buf[len];
    error_string(msg, buflen - len);
    len += (int)strlen(msg);
    char *p = &buf[len];

    jio_snprintf(p, buflen - len,
               "\n\n"
               "Do you want to debug the problem?\n\n"
               "To debug, run '%s'; then switch to thread " INTX_FORMAT " (" INTPTR_FORMAT ")\n"
               "Enter 'yes' to launch " GDB_CMD " automatically (PATH must include " GDB_CMD ")\n"
               "Otherwise, press RETURN to abort...",
               buf, tid, tid);

    yes = os::message_box("Unexpected Error", msg);

    if (yes) {
      // yes, user asked VM to launch debugger
      os::fork_and_exec(buf);
      yes = false;
    }
  } while (yes);
}

// Space for our "saved" signal flags and handlers
static int resettedSigflags[2];
static address resettedSighandler[2];

static void save_signal(int idx, int sig)
{
  struct sigaction sa;
  sigaction(sig, NULL, &sa);
  resettedSigflags[idx]   = sa.sa_flags;
  resettedSighandler[idx] = (sa.sa_flags & SA_SIGINFO)
                              ? CAST_FROM_FN_PTR(address, sa.sa_sigaction)
                              : CAST_FROM_FN_PTR(address, sa.sa_handler);
}

int VMError::get_resetted_sigflags(int sig) {
  if(SIGSEGV == sig) {
    return resettedSigflags[0];
  } else if(SIGBUS == sig) {
    return resettedSigflags[1];
  }
  return -1;
}

address VMError::get_resetted_sighandler(int sig) {
  if(SIGSEGV == sig) {
    return resettedSighandler[0];
  } else if(SIGBUS == sig) {
    return resettedSighandler[1];
  }
  return NULL;
}

static void crash_handler(int sig, siginfo_t* info, void* ucVoid) {
  // unmask current signal
  sigset_t newset;
  sigemptyset(&newset);
  sigaddset(&newset, sig);
  sigprocmask(SIG_UNBLOCK, &newset, NULL);

  VMError err(NULL, sig, NULL, info, ucVoid);
  err.report_and_die();
}

void VMError::reset_signal_handlers() {
  // Save sigflags for resetted signals
  save_signal(0, SIGSEGV);
  save_signal(1, SIGBUS);
  os::signal(SIGSEGV, CAST_FROM_FN_PTR(void *, crash_handler));
  os::signal(SIGBUS, CAST_FROM_FN_PTR(void *, crash_handler));
}
