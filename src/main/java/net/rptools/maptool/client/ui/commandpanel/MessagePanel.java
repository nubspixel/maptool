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
package net.rptools.maptool.client.ui.commandpanel;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ToolTipManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import net.rptools.lib.sound.SoundManager;
import net.rptools.maptool.client.AppPreferences;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.MacroLinkFunction;
import net.rptools.maptool.client.swing.MessagePanelEditorKit;
import net.rptools.maptool.model.TextMessage;
import net.rptools.maptool.util.MessageUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;
import java.util.ArrayList;

public class MessagePanel extends JPanel {

  private final JScrollPane scrollPane;
  private final HTMLDocument document;
  private final JEditorPane textPane;

  private static final String SND_MESSAGE_RECEIVED = "messageReceived";

  /** From ImageView */
  private static final String IMAGE_CACHE_PROPERTY = "imageCache";

  public static final Pattern URL_PATTERN =
      Pattern.compile("([^:]*)://([^/]*)/([^?]*)(?:\\?(.*))?");

  private static final String ChatSavePath = System.getProperty("user.home") + "/.maptool-rptools/autosave";

  public MessagePanel() {
    setLayout(new GridLayout());

    textPane = new JEditorPane();
    textPane.setEditable(false);
    textPane.setEditorKit(new MessagePanelEditorKit());
    textPane.addComponentListener(
        new ComponentListener() {
          public void componentHidden(ComponentEvent e) {}

          public void componentMoved(ComponentEvent e) {}

          public void componentResized(ComponentEvent e) {
            // Jump to the bottom on new text
            if (!MapTool.getFrame().getCommandPanel().getScrollLockButton().isSelected()) {
              Rectangle rowBounds = new Rectangle(0, textPane.getSize().height, 1, 1);
              textPane.scrollRectToVisible(rowBounds);
            }
          }

          public void componentShown(ComponentEvent e) {}
        });
    textPane.addHyperlinkListener(
        e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            if (e.getURL() != null) {
              MapTool.showDocument(e.getURL().toString());
            } else if (e.getDescription().startsWith("#")) {
              textPane.scrollToReference(e.getDescription().substring(1)); // scroll to the anchor
            } else {
              Matcher m = URL_PATTERN.matcher(e.getDescription());
              if (m.matches() && m.group(1).equalsIgnoreCase("macro")) {
                MacroLinkFunction.runMacroLink(e.getDescription());
              }
            }
          }
        });
    ToolTipManager.sharedInstance().registerComponent(textPane);

    document = (HTMLDocument) textPane.getDocument();

    // Initialize and prepare for usage
    refreshRenderer();

    scrollPane =
        new JScrollPane(
            textPane,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    scrollPane.getViewport().setBorder(null);
    scrollPane.getViewport().setBackground(Color.white);
    scrollPane
        .getVerticalScrollBar()
        .addMouseMotionListener(
            new MouseMotionAdapter() {
              @Override
              public void mouseDragged(MouseEvent e) {

                boolean lock =
                    (scrollPane.getSize().height + scrollPane.getVerticalScrollBar().getValue())
                        < scrollPane.getVerticalScrollBar().getMaximum();

                // The user has manually scrolled the scrollbar, Scroll lock time baby !
                MapTool.getFrame().getCommandPanel().getScrollLockButton().setSelected(lock);
              }
            });

    add(scrollPane);
    clearMessages();

    SoundManager.registerSoundEvent(SND_MESSAGE_RECEIVED, SoundManager.getRegisteredSound("Clink"));
  }

  public void refreshRenderer() {
    // Create the style
    StyleSheet style = document.getStyleSheet();
    style.addRule(
        "body { font-family: sans-serif; font-size: " + AppPreferences.getFontSize() + "pt}");
    style.addRule("div {margin-bottom: 5px}");
    style.addRule(".roll {background:#efefef}");
    setTrustedMacroPrefixColors(
        AppPreferences.getTrustedPrefixFG(), AppPreferences.getTrustedPrefixBG());
    style.addRule(MessageUtil.getMessageCss());
    repaint();
  }

  public void setTrustedMacroPrefixColors(Color foreground, Color background) {
    StyleSheet style = document.getStyleSheet();
    String css =
        String.format(
            ".trusted-prefix { color: #%06X; background: #%06X }",
            (foreground.getRGB() & 0xFFFFFF), (background.getRGB() & 0xFFFFFF));
    style.addRule(css);
    repaint();
  }

  public String getMessagesText() {

    return textPane.getText();
  }

  public void clearMessages() {
    EventQueue.invokeLater(
        () -> {
          textPane.setText("<html><body id=\"body\"></body></html>");
          ((MessagePanelEditorKit) textPane.getEditorKit()).flush();
        });
  }

  /*
   * We use ASCII control characters to mark off the rolls so that there's no limitation on what (printable) characters the output can include Rolls look like "\036roll output\036" or
   * "\036tooltip\037roll output\036" or "\036\001format info\002roll output\036" or "\036\001format info\002tooltip\037roll output\036"
   */
  private static Pattern roll_pattern =
      Pattern.compile("\036(?:\001([^\002]*)\002)?([^\036\037]*)(?:\037([^\036]*))?\036");

  public void addMessage(final TextMessage message) {
    EventQueue.invokeLater(
        () -> {
          String output;

          {
            StringBuffer text = new StringBuffer();
            Matcher m = roll_pattern.matcher(message.getMessage());
            while (m.find()) {
              HashSet<String> options = new HashSet<String>();
              if (m.group(1) != null) {
                options.addAll(Arrays.asList(m.group(1).split(",")));

                if (!options.contains("w") && !options.contains("g") && !options.contains("s"))
                  ; // visible for everyone
                else if (options.contains("w:" + MapTool.getPlayer().getName().toLowerCase()))
                  ; // visible for this player
                else if (options.contains("g") && MapTool.getPlayer().isGM()) ; // visible for GMs
                else if (options.contains("s")
                    && message.getSource().equals(MapTool.getPlayer().getName()))
                  ; // visible to the player who sent it
                else {
                  m.appendReplacement(text, ""); // not visible for this player
                  continue;
                }
              }
              String replacement = null;
              if (m.group(3) != null) {
                if (!options.contains("st") && !options.contains("gt")
                    || options.contains("st")
                        && message.getSource().equals(MapTool.getPlayer().getName())
                    || options.contains("gt") && MapTool.getPlayer().isGM())
                  replacement = "<span class='roll' title='&#171; $2 &#187;'>$3</span>";
                else replacement = "$3";
              } else if (options.contains("u")) replacement = "&#171; $2 &#187;";
              else if (options.contains("r")) replacement = "$2";
              else
                replacement =
                    "&#171;<span class='roll' style='color:blue'>&nbsp;$2&nbsp;</span>&#187;";
              m.appendReplacement(text, replacement);
            }
            m.appendTail(text);
            output = text.toString();
          }
          // Auto inline expansion for {HTTP|HTTPS} URLs
          // output = output.replaceAll("(^|\\s|>|\002)(https?://[\\w.%-/~?&+#=]+)", "$1<a
          // href='$2'>$2</a>");
          output =
              output.replaceAll(
                  "(^|\\s|>|\002)(https?://[^<>\002\003\\s]+)", "$1<a href='$2'>$2</a>");

          if (!message.getSource().equals(MapTool.getPlayer().getName())) {
            // TODO change this so 'macro' is case-insensitive
            Matcher m =
                Pattern.compile(
                        "href=([\"'])\\s*(macro://(?:[^/]*)/(?:[^?]*)(?:\\?(?:.*?))?)\\1\\s*",
                        Pattern.CASE_INSENSITIVE)
                    .matcher(output);
            while (m.find()) {
              MacroLinkFunction.getInstance().processMacroLink(m.group(2));
            }
          }
          // if rolls not being visible to this user result in an empty message, display nothing
          // TODO The leading and trailing '.*' are probably not needed -- test this before
          // removing them
          if (!output.matches(".*\002\\s*\003.*")) {
            output = output.replaceAll("\002|\003", "");

            try {
              Element element = document.getElement("body");
              if (!output.toLowerCase().startsWith("<div") || !output.endsWith("</div>")) {
                document.insertBeforeEnd(element, "<div>" + output + "</div>");
              } else {
                document.insertBeforeEnd(element, output);
              }

              String chatfilename = (! MapTool.getPlayer().isGM() ? "chat_data_player.txt" : "chat_data.txt");
              File chatFile = new File(ChatSavePath, chatfilename);
              try {
                Scanner scanner = new Scanner(chatFile);
                ArrayList<String> text_data = new ArrayList<String>();
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    text_data.add(line);
                }
                scanner.close();

                // process new chat message
                // set proper path for image
                String find_regex = "<img src=(?:\"|\')asset://([a-z0-9:/]+)-([0-9]+)(?:\"|\')";
                output = output.replaceAll(find_regex, "<img src=\"../assetcache/$1\" width=\"$2\"");

                // set proper path for sized <image>
                find_regex = "<image src=(?:\"|\')asset://([a-z0-9:/]+)(?:\"|\') width=(?:\"|\')([0-9]+)(?:\"|\')";
                output = output.replaceAll(find_regex, "<image src=\"../assetcache/$1\" width=\"$2\"");

                // set proper path for non-sized <image>
                find_regex = "<image src=(?:\"|\')asset://([a-z0-9:/]+)(?:\"|\')";
                output = output.replaceAll(find_regex, "<image src=\"../assetcache/$1\" width=\"10\"");

                // remove empty spaces
                find_regex = "(?:[ ]{4,99})";
                output = output.replaceAll(find_regex, "");

                // remove return carriage or new line
                find_regex = "(?:\\r?\\n)";
                output = output.replaceAll(find_regex, "");
                String timestamp = Long.toString(System.currentTimeMillis());
                text_data.add("<div class=\"chats\" data-ts=\""+timestamp+"\">" + output + "</div>");

                while(text_data.size() > 100) {
                  text_data.remove(0);
                }

                try (FileWriter writer = new FileWriter(chatFile)) {
                  for (String line : text_data) {
                      writer.write(line+System.getProperty( "line.separator" ));
                  }
                } catch (IOException e) {
                  MapTool.showWarning("msg.warn.failedSaveWriteFromChatWindow", e);
                }
              } catch (IOException e) {
                MapTool.showWarning("msg.warn.failToReadChatData", e);
              }

              if (!message.getSource().equals(MapTool.getPlayer().getName())) {
                MapTool.playSound(SND_MESSAGE_RECEIVED);
              }
            } catch (IOException | BadLocationException ioe) {
              ioe.printStackTrace();
            }
          }
        });
  }
}
