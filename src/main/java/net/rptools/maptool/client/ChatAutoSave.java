/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import net.rptools.maptool.client.ui.commandpanel.CommandPanel;
import net.rptools.maptool.language.I18N;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author frank */
public class ChatAutoSave {
  private static Logger log = LogManager.getLogger(ChatAutoSave.class);
  private static final ChatAutoSave self = new ChatAutoSave();

  private final Timer countdown;
  private TimerTask task;
  private long delay;
  private static String chatlog = null;

  private final Timer fast_countdown;
  private TimerTask fast_task;
  private long fast_delay;
  private static String fast_chatlog = null;

  private static final String start_log =
      "<!DOCTYPE html>\n<html>\n<head>\n<link rel=\"stylesheet\" type=\"text/css\" href=\"./styles.css\" />\n</head>\n<body>\n<div class=\"scroll-bottom\">\n";
  // "<!DOCTYPE html>\n<html>\n<head>\n<meta http-equiv=\"refresh\" content=\"5\" />\n<link
  // rel=\"stylesheet\" type=\"text/css\" href=\"./styles.css\" />\n<script
  // type=\"text/javascript\">setTimeout(function(){ window.location.reload(); },
  // 1000);</script>\n</head>\n<body>\n<div class=\"scroll-bottom\">\n";
  private static final String end_log = "<a id=\"end\"></a>\n</div>\n</body>\n</html>";

  private ChatAutoSave() {
    log.debug("Creating chat log autosave timer"); // $NON-NLS-1$
    // Only way to set the delay is to call changeTimeout()
    delay = 0;
    countdown = new Timer();

    fast_delay = 0;
    fast_countdown = new Timer();
  }

  private static TimerTask createTimer(final long timeout) {
    TimerTask t =
        new TimerTask() {
          @Override
          public void run() {
            if (log.isDebugEnabled())
              log.debug("Chat log autosave countdown complete from " + timeout); // $NON-NLS-1$
            if (chatlog == null) {
              String filename = AppPreferences.getChatFilenameFormat();
              // FJE Ugly kludge to replace older default entry with newer default
              // TODO This is going into 1.3.b77 so remove it in 3-4 builds
              if ("chatlog.html".equals(filename)) { // $NON-NLS-1$
                AppPreferences.clearChatFilenameFormat();
                filename = AppPreferences.getChatFilenameFormat();
              }
              chatlog = String.format(filename, new Date()).replace(':', '-');
            }
            File chatFile =
                new File(AppUtil.getAppHome("autosave").toString(), chatlog); // $NON-NLS-1$
            if (log.isInfoEnabled())
              log.info("Saving log to '" + chatFile + "'"); // $NON-NLS-1$ //$NON-NLS-2$

            CommandPanel chat = MapTool.getFrame().getCommandPanel();
            String old = MapTool.getFrame().getStatusMessage();
            try {
              MapTool.getFrame()
                  .setStatusMessage(
                      I18N.getString("ChatAutoSave.status.chatAutosave")); // $NON-NLS-1$
              try (FileWriter writer = new FileWriter(chatFile)) {
                writer.write(chat.getMessageHistory());
              }
              if (log.isInfoEnabled()) log.info("Log saved"); // $NON-NLS-1$
            } catch (IOException e) {
              // If this happens should we track it and turn off the autosave? Perhaps
              // after a certain number of consecutive failures? Or maybe just lengthen
              // the amount of time between attempts in that case? At a minimum we
              // should probably give the user a chance to turn it off as part of this
              // message box that pops up...
              MapTool.showWarning("msg.warn.failedAutoSavingMessageHistory", e); // $NON-NLS-1$
            } finally {
              MapTool.getFrame().setStatusMessage(old);
            }
          }
        };
    return t;
  }

  private static ChatAutoSave getInstance() {
    return self;
  }

  public static void changeTimeout(int timeout) {
    getInstance().delay = timeout * 1000 * 60;
    getInstance().start();

    getInstance().fast_delay = timeout * 1000;
    getInstance().fast_start();
  }

  private void stop() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  private void start() {
    if (delay > 0) {
      stop();
      task = createTimer(delay);
      countdown.schedule(task, 5000, delay); // Wait 5s, then save the log every 'delay' ms
    }
  }

  private static TimerTask fast_createTimer(final long timeout) {
    TimerTask t =
        new TimerTask() {
          @Override
          public void run() {
            // if (fast_chatlog == null) {
            //  String filename = "c"+AppPreferences.getChatFilenameFormat();
            //  // FJE Ugly kludge to replace older default entry with newer default
            //  // TODO This is going into 1.3.b77 so remove it in 3-4 builds
            //  if ("cchatlog.html".equals(filename)) { // $NON-NLS-1$
            //    AppPreferences.clearChatFilenameFormat();
            //    filename = AppPreferences.getChatFilenameFormat();
            //  }
            //  fast_chatlog = String.format(filename, new Date()).replace(':', '-');
            // }

            if (MapTool.getPlayer().isGM() && MapTool.isHostingServer())
              fast_chatlog = "chatlog_gm.html";
            else {
              fast_chatlog = "chatlog_player.html";
            }

            File chatFile =
                new File(AppUtil.getAppHome("autosave").toString(), fast_chatlog); // $NON-NLS-1$

            CommandPanel chat = MapTool.getFrame().getCommandPanel();
            // String old = MapTool.getFrame().getStatusMessage();
            try {
              // disable the status message saying its saving (every seconds)
              // MapTool.getFrame()
              //     .setStatusMessage(
              //         I18N.getString("ChatAutoSave.status.chatAutosave")); // $NON-NLS-1$
              try (FileWriter writer = new FileWriter(chatFile)) {
                String chatmsg = chat.getMessageHistory();
                String find_regex = "(?:[ ]*)</?html>(?:[ \n]*)|";
                find_regex += "(?:[ ]*)</?head>(?:[ \n]*)|";
                find_regex += "(?:[ ]*)</?style(?:[ a-z0-9=\"/]*)>(?:[ \n]*)|";
                find_regex += "(?:[ ]*)<!--(?:[.* a-zA-Z0-9-{:;}#\n]*)-->(?:[ \n]*)|";
                find_regex += "(?:[ ]*)</?body(?:[ a-z0-9=\"/]*)>(?:[ \n]*)|";
                find_regex += "(?:[ ]*)</?table(?:[ a-z0-9=\"]*)>(?:[ \n]*)|";
                find_regex += "(?:[ ]*)</?tr>(?:[ \n]*)|";
                find_regex += "(?:[ ]*)</?td(?:[ a-zA-Z0-9-\":;\"=]*)>(?:[ \n]*)|";
                find_regex += "(?:[ ]*)</?div>(?:[ \n]*)";
                find_regex += "(?:[ ]*)<img src=\"(?:[a-z0-9-:/]*)\">(?:[ \n]*)|";
                find_regex += "(?:[\n]*)";
                chatmsg = chatmsg.replaceAll(find_regex, "");

                find_regex = "(?:[ ]{2,99})";
                chatmsg = chatmsg.replaceAll(find_regex, " ");

                find_regex = "</div>";
                chatmsg = chatmsg.replaceAll(find_regex, "</div>\n");

                // add html lines
                // chatmsg = chatmsg.replaceAll("(</td>\n?)", "<br/>\n");
                // chatmsg = chatmsg.replaceAll("(</span>\n)", "</span><br/>\n");

                // trim lines old lines, so it only save to a certain limit
                chatmsg = trimLines(chatmsg, 100);

                // add basic html stuff
                chatmsg = start_log + chatmsg + end_log;

                // chatmsg = chatmsg.replaceAll("(?:[ \n]{2,99})", " ");

                // chatmsg = "\nisGM: "+MapTool.getPlayer().isGM()+" | isHostingServer"+
                // MapTool.isHostingServer() +" | isPersonalServer"+ MapTool.isPersonalServer()
                // +"\n";
                writer.write(chatmsg);
              }
            } catch (IOException e) {
              // If this happens should we track it and turn off the autosave? Perhaps
              // after a certain number of consecutive failures? Or maybe just lengthen
              // the amount of time between attempts in that case? At a minimum we
              // should probably give the user a chance to turn it off as part of this
              // message box that pops up...
              MapTool.showWarning("msg.warn.failedAutoSavingMessageHistory", e); // $NON-NLS-1$
            } finally {
              // disable the status message saying its saving (every seconds)
              // MapTool.getFrame().setStatusMessage(old);
            }
          }
        };
    return t;
  }

  private void fast_stop() {
    if (fast_task != null) {
      fast_task.cancel();
      fast_task = null;
    }
  }

  private void fast_start() {
    if (fast_delay > 0) {
      fast_stop();
      fast_task = fast_createTimer(fast_delay);
      fast_countdown.schedule(
          fast_task, 1000, fast_delay); // Wait 5s, then save the log every 'delay' ms
    }
  }

  private static int countLines(String str) {
    String[] lines = str.split("\r\n|\r|\n");
    return lines.length;
  }

  private static String trimLines(String str, int max_lines) {
    if (countLines(str) > 100) {
      str = str.substring(str.indexOf('\n') + 1);
      return trimLines(str, max_lines);
    }
    return str;
  }
}
