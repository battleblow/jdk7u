/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @bug 8032808
 * @run main/othervm -Xcheck:jni Test
 * @run main/othervm/policy=policy.fail -Xcheck:jni Test fail
 * @run main/othervm/policy=policy.success -Xcheck:jni Test success
 */

import java.net.*;
import java.nio.channels.*;
import java.util.concurrent.*;
import jdk.net.*;

public class Test {

    static boolean security;
    static boolean success;
    static int udp_port;
    static InetAddress loop;
    static InetSocketAddress loopad;
    static SocketFlow flowIn;
    static SocketChannel sc;
    static Socket s;

    public static void main(String[] args) throws Exception {

        // quick check to see if supportedOptions() working before
        // creating any sockets and libnet loaded

        Sockets.supportedOptions(Socket.class);

        security = System.getSecurityManager() != null;
        success = security && args[0].equals("success");

        // Main thing is to check for JNI problems
        // Doesn't matter if current system does not support the option
        // and currently setting the option with the loopback interface
        // doesn't work either

        System.out.println ("Security Manager enabled: " + security);
        if (security) {
            System.out.println ("Success expected: " + success);
        }

        flowIn = SocketFlow.create()
            .bandwidth(1000)
            .priority(SocketFlow.HIGH_PRIORITY);

        ServerSocket ss = new ServerSocket(0);
        int tcp_port = ss.getLocalPort();
        loop = InetAddress.getByName("127.0.0.1");
        loopad = new InetSocketAddress(loop, tcp_port);

        DatagramSocket dg = new DatagramSocket(0);
        udp_port = dg.getLocalPort();

        s = new Socket("127.0.0.1", tcp_port);
        sc = SocketChannel.open();
        sc.connect (new InetSocketAddress("127.0.0.1", tcp_port));

        // Do some standard options tests first. Since JDK 8 doesn't have java.net API
        Sockets.setOption(s, StandardSocketOptions.SO_SNDBUF, 8000);
        System.out.println ("Set SO_SNDBUF to 8000\ngetting returns: ");
        System.out.println (Sockets.getOption(s, StandardSocketOptions.SO_SNDBUF));

        Sockets.setOption(ss, StandardSocketOptions.SO_RCVBUF, 5000);
        System.out.println ("Set SO_RCVBUF to 5000\ngetting returns: ");
        System.out.println (Sockets.getOption(s, StandardSocketOptions.SO_RCVBUF));

        try {
            Sockets.setOption(ss, StandardSocketOptions.TCP_NODELAY, true);
            throw new RuntimeException("TCP_NODELAY should not be supported for ServerSocket");
        } catch (UnsupportedOperationException e) {}
        try {
            Sockets.setOption(dg, StandardSocketOptions.IP_MULTICAST_LOOP, true);
            throw new RuntimeException("IP_MULTICAST_LOOP should not be supported for DatagramSocket");
        } catch (UnsupportedOperationException e) {}

        MulticastSocket mc0 = new MulticastSocket(0);
        Sockets.setOption(mc0, StandardSocketOptions.IP_MULTICAST_LOOP, true);
        System.out.println ("Expect true: " + Sockets.getOption(mc0, StandardSocketOptions.IP_MULTICAST_LOOP));

        // Now the specific tests for SO_FLOW_SLA

        doTest1();
        doTest2();
        doTest3();
        doTest4();
        doTest5();
        doTest6();
        doTest7();
        doTest8();
    }

    static void doTest1() throws Exception {
        try {
            Sockets.setOption(s, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }

    static void doTest2() throws Exception {
        try {
            Sockets.getOption(s, ExtendedSocketOptions.SO_FLOW_SLA);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }

    static void doTest3() throws Exception {
        try {
            sc.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }

    static void doTest4() throws Exception {
        try {
            sc.getOption(ExtendedSocketOptions.SO_FLOW_SLA);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }

    static void doTest5() throws Exception {
        try {
            DatagramSocket dg1 = new DatagramSocket(0);
            dg1.connect(loop, udp_port);
            Sockets.setOption(dg1, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }

    static void doTest6() throws Exception {
        try {
            DatagramChannel dg2 = DatagramChannel.open();
            dg2.bind(new InetSocketAddress(loop, 0));
            dg2.connect(new InetSocketAddress(loop, udp_port));
            dg2.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }

    static void doTest7() throws Exception {
        try {
            MulticastSocket mc1 = new MulticastSocket(0);
            mc1.connect(loop, udp_port);
            Sockets.setOption(mc1, ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }

    static void doTest8() throws Exception {
        try {
            AsynchronousSocketChannel asc = AsynchronousSocketChannel.open();
            Future<Void> f = asc.connect(loopad);
            f.get();
            asc.setOption(ExtendedSocketOptions.SO_FLOW_SLA, flowIn);
            if (security && !success) {
                throw new RuntimeException("Test failed");
            }
        } catch (SecurityException e) {
            if (success) {
                throw new RuntimeException("Test failed");
            }
        } catch (UnsupportedOperationException e) {}
    }
}
