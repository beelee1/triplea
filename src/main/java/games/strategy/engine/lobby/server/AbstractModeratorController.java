package games.strategy.engine.lobby.server;

import java.util.logging.Logger;

import games.strategy.engine.message.IRemoteMessenger;
import games.strategy.engine.message.RemoteName;
import games.strategy.net.INode;
import games.strategy.net.IServerMessenger;
import games.strategy.net.Messengers;

public abstract class AbstractModeratorController implements IModeratorController {
  protected static final Logger logger = Logger.getLogger(ModeratorController.class.getName());
  protected final IServerMessenger serverMessenger;
  protected final Messengers allMessengers;

  public AbstractModeratorController(final IServerMessenger serverMessenger, final Messengers messengers) {
    this.serverMessenger = serverMessenger;
    allMessengers = messengers;
  }

  public static RemoteName getModeratorControllerName() {
    return new RemoteName(IModeratorController.class, "games.strategy.engine.lobby.server.ModeratorController:Global");
  }

  public void register(final IRemoteMessenger messenger) {
    messenger.registerRemote(this, getModeratorControllerName());
  }

  protected String getNodeMacAddress(final INode node) {
    return serverMessenger.getPlayerMac(node.getName());
  }

  protected String getRealName(final INode node) {
    // Remove any (n) that is added to distinguish duplicate names
    return node.getName().split(" ")[0];
  }

  protected String getAliasesFor(final INode node) {
    final StringBuilder builder = new StringBuilder();
    final String nodeMac = getNodeMacAddress(node);
    for (final INode cur : serverMessenger.getNodes()) {
      if (cur.equals(node) || cur.getName().equals("Admin")) {
        continue;
      }
      if (cur.getAddress().equals(node.getAddress()) || getNodeMacAddress(cur).equals(nodeMac)) {
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder.append(cur.getName());
      }
    }
    if (builder.length() > 100) {
      // So replace comma's to keep names within screen
      return builder.toString().replace(", ", "\r\n");
    }
    return builder.toString();
  }
}
