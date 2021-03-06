package games.strategy.engine.message.unifiedmessenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import games.strategy.debug.ClientLogger;
import games.strategy.engine.message.ChannelMessenger;
import games.strategy.engine.message.IChannelSubscribor;
import games.strategy.engine.message.RemoteName;
import games.strategy.engine.message.UnifiedMessengerHub;
import games.strategy.net.ClientMessenger;
import games.strategy.net.IMessenger;
import games.strategy.net.IServerMessenger;
import games.strategy.net.MacFinder;
import games.strategy.net.ServerMessenger;
import games.strategy.util.ThreadUtil;

public class ChannelMessengerTest {
  private IServerMessenger serverMessenger;
  private IMessenger clientMessenger;
  private int serverPort = -1;
  private ChannelMessenger serverChannelMessenger;
  private ChannelMessenger clientChannelMessenger;
  private UnifiedMessengerHub unifiedMessengerHub;

  @BeforeEach
  public void setUp() throws IOException {
    serverMessenger = new ServerMessenger("Server", 0);
    serverMessenger.setAcceptNewConnections(true);
    serverPort = serverMessenger.getLocalNode().getSocketAddress().getPort();
    final String mac = MacFinder.getHashedMacAddress();
    clientMessenger = new ClientMessenger("localhost", serverPort, "client1", mac);
    final UnifiedMessenger unifiedMessenger = new UnifiedMessenger(serverMessenger);
    unifiedMessengerHub = unifiedMessenger.getHub();
    serverChannelMessenger = new ChannelMessenger(unifiedMessenger);
    clientChannelMessenger = new ChannelMessenger(new UnifiedMessenger(clientMessenger));
  }

  @AfterEach
  public void tearDown() {
    try {
      if (serverMessenger != null) {
        serverMessenger.shutDown();
      }
    } catch (final Exception e) {
      ClientLogger.logQuietly(e);
    }
    try {
      if (clientMessenger != null) {
        clientMessenger.shutDown();
      }
    } catch (final Exception e) {
      ClientLogger.logQuietly(e);
    }
  }

  @Test
  public void testLocalCall() {
    final RemoteName descriptor = new RemoteName(IChannelBase.class, "testLocalCall");
    serverChannelMessenger.registerChannelSubscriber(new ChannelSubscribor(), descriptor);
    final IChannelBase subscribor = (IChannelBase) serverChannelMessenger.getChannelBroadcastor(descriptor);
    subscribor.testNoParams();
    subscribor.testPrimitives(1, (short) 0, 1, (byte) 1, true, (float) 1.0);
    subscribor.testString("a");
  }

  @Test
  public void testRemoteCall() {
    final RemoteName testRemote = new RemoteName(IChannelBase.class, "testRemote");
    final ChannelSubscribor subscribor1 = new ChannelSubscribor();
    serverChannelMessenger.registerChannelSubscriber(subscribor1, testRemote);
    assertHasChannel(testRemote, unifiedMessengerHub);
    final IChannelBase channelTest = (IChannelBase) clientChannelMessenger.getChannelBroadcastor(testRemote);
    channelTest.testNoParams();
    assertCallCountIs(subscribor1, 1);
    channelTest.testString("a");
    assertCallCountIs(subscribor1, 2);
    channelTest.testPrimitives(1, (short) 0, 1, (byte) 1, true, (float) 1.0);
    assertCallCountIs(subscribor1, 3);
    channelTest.testArray(null, null, null, null, null, null);
    assertCallCountIs(subscribor1, 4);
  }

  @Test
  public void testMultipleClients() throws Exception {
    // set up the client and server
    // so that the client has 1 subscribor, and the server knows about it
    final RemoteName test = new RemoteName(IChannelBase.class, "test");
    final ChannelSubscribor client1Subscribor = new ChannelSubscribor();
    clientChannelMessenger.registerChannelSubscriber(client1Subscribor, test);
    assertHasChannel(test, unifiedMessengerHub);
    assertEquals(1, clientChannelMessenger.getUnifiedMessenger().getLocalEndPointCount(test));
    // add a new client
    final String mac = MacFinder.getHashedMacAddress();
    final ClientMessenger clientMessenger2 = new ClientMessenger("localhost", serverPort, "client2", mac);
    final ChannelMessenger client2 = new ChannelMessenger(new UnifiedMessenger(clientMessenger2));
    ((IChannelBase) client2.getChannelBroadcastor(test)).testString("a");
    assertCallCountIs(client1Subscribor, 1);
  }

  @Test
  public void testMultipleChannels() {
    final RemoteName testRemote2 = new RemoteName(IChannelBase.class, "testRemote2");
    final RemoteName testRemote3 = new RemoteName(IChannelBase.class, "testRemote3");
    final ChannelSubscribor subscribor2 = new ChannelSubscribor();
    clientChannelMessenger.registerChannelSubscriber(subscribor2, testRemote2);
    final ChannelSubscribor subscribor3 = new ChannelSubscribor();
    clientChannelMessenger.registerChannelSubscriber(subscribor3, testRemote3);
    assertHasChannel(testRemote2, unifiedMessengerHub);
    assertHasChannel(testRemote3, unifiedMessengerHub);
    final IChannelBase channelTest2 = (IChannelBase) serverChannelMessenger.getChannelBroadcastor(testRemote2);
    channelTest2.testNoParams();
    assertCallCountIs(subscribor2, 1);
    final IChannelBase channelTest3 = (IChannelBase) serverChannelMessenger.getChannelBroadcastor(testRemote3);
    channelTest3.testNoParams();
    assertCallCountIs(subscribor3, 1);
  }

  private static void assertHasChannel(final RemoteName descriptor, final UnifiedMessengerHub hub) {
    int waitCount = 0;
    while (waitCount < 10 && !hub.hasImplementors(descriptor.getName())) {
      ThreadUtil.sleep(100);
      waitCount++;
    }
    assertTrue(hub.hasImplementors(descriptor.getName()));
  }

  private static void assertCallCountIs(final ChannelSubscribor subscribor, final int expected) {
    // since the method call happens in a seperate thread,
    // wait for the call to go through, but dont wait too long
    int waitCount = 0;
    while (waitCount < 20 && expected != subscribor.getCallCount()) {
      ThreadUtil.sleep(50);
      waitCount++;
    }
    assertEquals(expected, subscribor.getCallCount());
  }

  private interface IChannelBase extends IChannelSubscribor {
    void testNoParams();

    void testPrimitives(int a, short b, long c, byte d, boolean e, float f);

    void testString(String a);

    void testArray(int[] ints, short[] shorts, byte[] bytes, boolean[] bools, float[] floats, Object[] objects);
  }

  private static class ChannelSubscribor implements IChannelBase {
    private int callCount = 0;

    private synchronized void incrementCount() {
      callCount++;
    }

    public synchronized int getCallCount() {
      return callCount;
    }

    @Override
    public void testNoParams() {
      incrementCount();
    }

    @Override
    public void testPrimitives(final int a, final short b, final long c, final byte d, final boolean e, final float f) {
      incrementCount();
    }

    @Override
    public void testString(final String a) {
      incrementCount();
    }

    @Override
    public void testArray(final int[] ints, final short[] shorts, final byte[] bytes, final boolean[] bools,
        final float[] floats, final Object[] objects) {
      incrementCount();
    }
  }
}
