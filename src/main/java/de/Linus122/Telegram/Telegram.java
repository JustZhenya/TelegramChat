package de.Linus122.Telegram;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.Linus122.TelegramComponents.ChatMessageToTelegram;
import de.Linus122.TelegramComponents.Message;
import de.Linus122.TelegramChat.TelegramChat;
import de.Linus122.TelegramComponents.Chat;
import de.Linus122.TelegramComponents.ChatMessageToMc;
import de.Linus122.TelegramComponents.Update;

public class Telegram {
	public String tgUsername;

	static int lastUpdate = 0;
	
	public String token;

	private List<TelegramActionListener> listeners = new ArrayList<TelegramActionListener>();

	private final String API_URL_ENDPOINT = "https://api.telegram.org/bot%s/%s";

	private Gson gson = new Gson();

	public void addListener(TelegramActionListener actionListener) {
		listeners.add(actionListener);
	}

	public boolean auth(String token) {
		this.token = token;
		try {
			tgUsername = tgRequest("getMe", "{}").getAsJsonObject().get("username").getAsString();
			TelegramChat.getInstance().getLogger().info("Authenticated as @" + tgUsername);
			return true;
		} catch (Exception e) {
			TelegramChat.getInstance().getLogger().warning("Failed to authenticate: " + e.getMessage());
			return false;
		}
	}

	public boolean handleUpdates() throws IOException {
		JsonElement updates = tgRequest("getUpdates", "{\"offset\":" + (lastUpdate+1) + "}");

		for (JsonElement update_obj : updates.getAsJsonArray()) {
			Update update = gson.fromJson(update_obj, Update.class);
			if(update.getUpdate_id() > lastUpdate)
				lastUpdate = update.getUpdate_id();

			Message message = update.getMessage();
			if(message == null)
				continue;

			Chat chat = message.getChat();
			long chat_id = chat.getId();

			synchronized (TelegramChat.mutex) {
				TelegramChat.getBackend().chat_ids.add(chat_id);
			}
			
			if (message.getText() == null)
				continue;
			
			String message_text = message.getText();
			if (message_text.length() == 0)
				continue;

			if (chat.isPrivate() && message_text.equals("/start")) {
				if (TelegramChat.getBackend().isFirstUse()) {
					TelegramChat.getBackend().setFirstUse(false);
					ChatMessageToTelegram chat2 = new ChatMessageToTelegram();
					chat2.chat_id = chat_id;
					chat2.parse_mode = "Markdown";
					chat2.text = Utils.formatMSG("setup-msg")[0];
					this.sendMsg(chat2);
				} else {
					this.sendMsg(chat_id, Utils.formatMSG("can-see-but-not-chat")[0]);
				}
				continue;
			}

			if(message_text.equals("/online") || message_text.equals("/online@just_zhenya_mc_chat_bot"))
			{
				this.sendMsg(chat_id, "Да, бот и сервер онлайн!");
				continue;
			}
			else if(message_text.equals("/linktelegram") || message_text.equals("/linktelegram@just_zhenya_mc_chat_bot"))
			{
				this.sendMsg(chat_id, "Эту команду нужно писать в чат игры, а не сюда.");
				continue;
			}
			
			handleUserMessage(message_text, message);
		}
		return true;
	}
	
	public void handleUserMessage(String text, Message msg) {
		Chat chat = msg.getChat();
		long user_id = msg.getFrom().getId();
		if (TelegramChat.getBackend().getLinkCodes().containsKey(text)) {
			// LINK
			TelegramChat.link(TelegramChat.getBackend().getUUIDFromLinkCode(text), user_id);
			TelegramChat.getBackend().removeLinkCode(text);
		} else if (TelegramChat.getBackend().getLinkedChats().containsKey(user_id)) {
			ChatMessageToMc chatMsg = new ChatMessageToMc(TelegramChat.getBackend().getUUIDFromUserID(user_id), text, chat.getId());
			
			for (TelegramActionListener actionListener : listeners) {
				actionListener.onSendToMinecraft(chatMsg);
			}
			
			if(!chatMsg.isCancelled()) {
				boolean skipFirstMessages = TelegramChat.getInstance().getConfig().getBoolean("omit-messages-sent-while-server-was-offline");
				
				long currentTime = System.currentTimeMillis() / 1000L;
				long messageTime = msg.getDate();

				if(skipFirstMessages && currentTime - messageTime > 10*60) {
					TelegramChat.getInstance().getLogger().info("Omitted message Telegram->MC because it was sent while the server was offline.");
				} else {
					TelegramChat.sendToMC(chatMsg);
				}
			}
		} else {
			boolean skipIfNeedToLinkSilent = TelegramChat.getInstance().getConfig().getBoolean("omit-messages-need-to-link");
			if (!skipIfNeedToLinkSilent) {
				this.sendMsg(chat.getId(), Utils.formatMSG("need-to-link")[0]);
			}
		}
	}

	public void sendMsg(long id, String msg) {
		ChatMessageToTelegram chat = new ChatMessageToTelegram();
		chat.chat_id = id;
		chat.text = msg;
		sendMsg(chat);
	}

	public void sendMsg(ChatMessageToTelegram chat) {
		for (TelegramActionListener actionListener : listeners) {
			actionListener.onSendToTelegram(chat);
		}
		chat.disable_notification = TelegramChat.getInstance().getConfig().getBoolean("turn-to-silent-notification");
		
		Gson gson = new Gson();
		if(!chat.isCancelled()) {
			try
			{
				tgRequest("sendMessage", gson.toJson(chat, ChatMessageToTelegram.class));	
			}
			catch (Exception e)
			{
				boolean remove = false;
				if(e.getMessage().equals("Telegram API Error: Forbidden: user is deactivated [403]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Bad Request: chat not found [400]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Forbidden: the group chat was deleted [403]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Forbidden: bot was blocked by the user [403]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Forbidden: bot was kicked from the group chat [403]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Forbidden: bot was kicked from the supergroup chat [403]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Forbidden: bot is not a member of the supergroup chat [403]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Bad Request: group chat was upgraded to a supergroup chat [400]"))
				{
					remove = true;
				}
				else if(e.getMessage().equals("Telegram API Error: Bad Request: not enough rights to send text messages to the chat [400]"))
				{
					remove = true;
					tgRequest("leaveChat", "{\"chat_id\":" + chat.chat_id + "}");
				}
				else
				{
					TelegramChat.getInstance().getLogger().warning("Failed to send msg to " + chat.chat_id + ": " + e.getMessage());
				}

				if(remove) {
					synchronized (TelegramChat.mutex) {
						TelegramChat.getBackend().chat_ids.remove((Long) chat.chat_id);
					}
					TelegramChat.getInstance().getLogger().info("Removed " + chat.chat_id + " from lists: " + e.getMessage());
				}
			}
		}
	}

	public void sendAll(final ChatMessageToTelegram chat) {
		new Thread(new Runnable() {
			public void run() {
				TreeSet<Long> chat_ids_copy = null;
				synchronized (TelegramChat.mutex) {
					chat_ids_copy = new TreeSet<Long>(TelegramChat.getBackend().chat_ids);
				}
				
				for (long id : chat_ids_copy) {
					chat.chat_id = id;
					sendMsg(chat);
				}
			}
		}).start();
	}

	public JsonElement tgRequest(String method, String json) {
		JsonObject json_response;

		try {
			URL url = new URL(String.format(API_URL_ENDPOINT, TelegramChat.getBackend().getToken(), method));
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(30000);
			connection.setRequestMethod("POST");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Content-Type", "application/json; ; Charset=UTF-8");
			connection.setRequestProperty("Content-Length", String.valueOf(json.length()));

			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(wr, "UTF-8"));
			writer.write(json);
			writer.close();
			wr.close();

			// ignore http errors
			InputStream is = null;
			if (connection.getResponseCode() >= 400) {
				is = connection.getErrorStream();
			} else {
				is = connection.getInputStream();
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(is));

			String response = "";
			String inputLine;
			while ((inputLine = reader.readLine()) != null) {
				response += inputLine;
			}

			writer.close();
			reader.close();

			JsonParser parser = new JsonParser();
			json_response = parser.parse(response).getAsJsonObject();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Telegram Request Error: " + e.getMessage());
		}

		if(!json_response.has("result")) {
			int error_code = json_response.get("error_code").getAsInt();
			String error_desc = json_response.get("description").getAsString();
			throw new RuntimeException("Telegram API Error: " + error_desc + " [" + error_code + "]");
		}

		return json_response.get("result");
	}
}
