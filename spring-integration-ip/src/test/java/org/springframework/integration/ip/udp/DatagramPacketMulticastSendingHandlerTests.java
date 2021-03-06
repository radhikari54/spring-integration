/*
 * Copyright 2002-2016 the original author or authors.
 *
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
 */

package org.springframework.integration.ip.udp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.LogFactory;
import org.junit.Rule;
import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

/**
 * @author Mark Fisher
 * @author Gary Russell
 * @since 2.0
 */
public class DatagramPacketMulticastSendingHandlerTests {

	@Rule
	public MulticastRule multicastRule = new MulticastRule();

	@Test
	public void verifySendMulticast() throws Exception {
		MulticastSocket socket;
		try {
			socket = new MulticastSocket();
		}
		catch (Exception e) {
			return;
		}
		final int testPort = socket.getLocalPort();
		final String multicastAddress = this.multicastRule.getGroup();
		final String payload = "foo";
		final CountDownLatch listening = new CountDownLatch(2);
		final CountDownLatch received = new CountDownLatch(2);
		Runnable catcher = () -> {
			try {
				byte[] buffer = new byte[8];
				DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
				MulticastSocket socket1 = new MulticastSocket(testPort);
				socket1.setInterface(InetAddress.getByName(multicastRule.getNic()));
				InetAddress group = InetAddress.getByName(multicastAddress);
				socket1.joinGroup(group);
				listening.countDown();
				LogFactory.getLog(getClass())
					.debug(Thread.currentThread().getName() + " waiting for packet");
				socket1.receive(receivedPacket);
				socket1.close();
				byte[] src = receivedPacket.getData();
				int length = receivedPacket.getLength();
				int offset = receivedPacket.getOffset();
				byte[] dest = new byte[length];
				System.arraycopy(src, offset, dest, 0, length);
				assertEquals(payload, new String(dest));
				LogFactory.getLog(getClass())
					.debug(Thread.currentThread().getName() + " received packet");
				received.countDown();
			}
			catch (Exception e) {
				listening.countDown();
				e.printStackTrace();
			}
		};
		Executor executor = Executors.newFixedThreadPool(2);
		executor.execute(catcher);
		executor.execute(catcher);
		assertTrue(listening.await(10000, TimeUnit.MILLISECONDS));
		MulticastSendingMessageHandler handler = new MulticastSendingMessageHandler(multicastAddress, testPort);
		handler.setBeanFactory(mock(BeanFactory.class));
		handler.setLocalAddress(this.multicastRule.getNic());
		handler.afterPropertiesSet();
		handler.handleMessage(MessageBuilder.withPayload(payload).build());
		assertTrue(received.await(10000, TimeUnit.MILLISECONDS));
		handler.stop();
		socket.close();
	}

	@Test
	public void verifySendMulticastWithAcks() throws Exception {

		MulticastSocket socket;
		try {
			socket = new MulticastSocket();
		}
		catch (Exception e) {
			return;
		}
		final int testPort = socket.getLocalPort();
		final AtomicInteger ackPort = new AtomicInteger();

		final String multicastAddress = "225.6.7.8";
		final String payload = "foobar";
		final CountDownLatch listening = new CountDownLatch(2);
		final CountDownLatch ackListening = new CountDownLatch(1);
		final CountDownLatch ackSent = new CountDownLatch(2);
		Runnable catcher = () -> {
			try {
				byte[] buffer = new byte[1000];
				DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
				MulticastSocket socket1 = new MulticastSocket(testPort);
				socket1.setInterface(InetAddress.getByName(multicastRule.getNic()));
				socket1.setSoTimeout(8000);
				InetAddress group = InetAddress.getByName(multicastAddress);
				socket1.joinGroup(group);
				listening.countDown();
				assertTrue(ackListening.await(10, TimeUnit.SECONDS));
				LogFactory.getLog(getClass()).debug(Thread.currentThread().getName() + " waiting for packet");
				socket1.receive(receivedPacket);
				socket1.close();
				byte[] src = receivedPacket.getData();
				int length = receivedPacket.getLength();
				int offset = receivedPacket.getOffset();
				byte[] dest = new byte[6];
				System.arraycopy(src, offset + length - 6, dest, 0, 6);
				assertEquals(payload, new String(dest));
				LogFactory.getLog(getClass()).debug(Thread.currentThread().getName() + " received packet");
				DatagramPacketMessageMapper mapper = new DatagramPacketMessageMapper();
				mapper.setAcknowledge(true);
				mapper.setLengthCheck(true);
				Message<byte[]> message = mapper.toMessage(receivedPacket);
				Object id = message.getHeaders().get(IpHeaders.ACK_ID);
				byte[] ack = id.toString().getBytes();
				DatagramPacket ackPack = new DatagramPacket(ack, ack.length,
											new InetSocketAddress(multicastRule.getNic(), ackPort.get()));
				DatagramSocket out = new DatagramSocket();
				out.send(ackPack);
				LogFactory.getLog(getClass()).debug(Thread.currentThread().getName() + " sent ack to "
						+ ackPack.getSocketAddress());
				out.close();
				ackSent.countDown();
				socket1.close();
			}
			catch (Exception e) {
				listening.countDown();
				e.printStackTrace();
			}
		};
		Executor executor = Executors.newFixedThreadPool(2);
		executor.execute(catcher);
		executor.execute(catcher);
		assertTrue(listening.await(10000, TimeUnit.MILLISECONDS));
		MulticastSendingMessageHandler handler =
			new MulticastSendingMessageHandler(multicastAddress, testPort, true, true, "localhost", 0, 10000);
		handler.setLocalAddress(this.multicastRule.getNic());
		handler.setMinAcksForSuccess(2);
		handler.setBeanFactory(mock(BeanFactory.class));
		handler.afterPropertiesSet();
		handler.start();
		waitAckListening(handler);
		ackPort.set(handler.getAckPort());
		ackListening.countDown();
		handler.handleMessage(MessageBuilder.withPayload(payload).build());
		assertTrue(ackSent.await(10000, TimeUnit.MILLISECONDS));
		handler.stop();
		socket.close();
	}

	public void waitAckListening(UnicastSendingMessageHandler handler) throws InterruptedException {
		int n = 0;
		while (n++ < 100 && handler.getAckPort() == 0) {
			Thread.sleep(100);
		}
		assertTrue(n < 100);
	}

}
