package de.Linus122.TelegramChat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import de.Linus122.Handlers.VanishHandler;
import de.myzelyam.api.vanish.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;

import de.Linus122.Handlers.BanHandler;
import de.Linus122.Metrics.Metrics;
import de.Linus122.Telegram.Telegram;
import de.Linus122.Telegram.Utils;
import de.Linus122.TelegramComponents.Chat;
import de.Linus122.TelegramComponents.ChatMessageToMc;
import de.Linus122.TelegramComponents.ChatMessageToTelegram;

public class TelegramChat extends JavaPlugin implements Listener {
	private static File datad = new File("plugins/TelegramChat/data.json");

	public static Object mutex = new Object();
	private static FileConfiguration cfg;

	private static Data data = new Data();
	public static Telegram telegramHook;
	private static TelegramChat instance;
	private static boolean isSuperVanish;
	private Thread longpollThread;
	public AtomicBoolean isRunning = new AtomicBoolean(true);

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		cfg = this.getConfig();
		instance = this;
		Utils.cfg = cfg;

		Bukkit.getPluginCommand("telegram").setExecutor(new TelegramCmd());
		Bukkit.getPluginCommand("linktelegram").setExecutor(new LinkTelegramCmd());
		Bukkit.getPluginManager().registerEvents(this, this);

		if (Bukkit.getPluginManager().isPluginEnabled("SuperVanish") || Bukkit.getPluginManager().isPluginEnabled("PremiumVanish")) {
			isSuperVanish = true;
			Bukkit.getPluginManager().registerEvents(new VanishHandler(), this);
		}

		File dir = new File("plugins/TelegramChat/");
		dir.mkdir();
		data = new Data();
		if (datad.exists()) {
			Gson gson = new Gson();
			try {
				FileReader fileReader = new FileReader(datad);
				StringBuilder sb = new StringBuilder();
				int c;
			    while((c = fileReader.read()) !=-1) {
			    	sb.append((char) c);
			    }

				data = (Data) gson.fromJson(sb.toString(), Data.class);
				
				fileReader.close();
			} catch (Exception e) {
				this.getLogger().log(Level.WARNING, "Can't open data.json: " + e.getMessage());
			}
		}

		telegramHook = new Telegram();
		telegramHook.auth(data.getToken());
		
		// Ban Handler (Prevents banned players from chatting)
		telegramHook.addListener(new BanHandler());
		
		// Console sender handler, allows players to send console commands (telegram.console permission)
		// telegramHook.addListener(new CommandHandler(telegramHook, this));

		longpollThread = new Thread(new Runnable() {
			public void run() {
				while(isRunning.get())
				{
					if(TelegramChat.telegramHook.tgUsername.isEmpty())
					{
						try {
							Thread.sleep(5000);
						} catch (InterruptedException e2) {
							// do not care
						}
						continue;
					}

					try
					{
						telegramHook.handleUpdates();
					}
					catch (Exception e)
					{
						TelegramChat.getInstance().getLogger().log(Level.WARNING, "handleUpdates() failed: " + e.getMessage());

						try {
							Thread.sleep(5000);
						} catch (InterruptedException e2) {
							// do not care
						}
					}
				}
			}
		});

		longpollThread.start();
		
		// metrics
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> new Metrics(this));
	}

	@Override
	public void onDisable() {
		save();
		isRunning.set(false);
		try {
			this.getLogger().info("Waiting to longpollThread to finish...");
			longpollThread.join();
		} catch (InterruptedException e) {
			// do not care
		}
		this.getLogger().info("longpollThread has finished");
	}

	public static void save() {
		Gson gson = new Gson();

		try {
			FileWriter fileWriter = new FileWriter(datad);
			fileWriter.write(gson.toJson(data));
			
			fileWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static Data getBackend() {
		return data;
	}

	public static void initBackend() {
		data = new Data();
	}

	public static void sendToMC(ChatMessageToMc chatMsg) {
		sendToMC(chatMsg.getUuid_sender(), chatMsg.getContent(), chatMsg.getChatID_sender());
	}

	private static void sendToMC(UUID uuid, String msg, long sender_chat) {
		OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
		String msgF = Utils.formatMSG("general-message-to-mc", op.getName(), msg)[0];

		Bukkit.broadcastMessage(msgF.replace("&", "§"));

		List<Long> recievers = new ArrayList<Long>();
		synchronized (TelegramChat.mutex) {
			recievers.addAll(TelegramChat.data.chat_ids);
		}
		recievers.remove((Long) sender_chat);
		for (long id : recievers) {
			telegramHook.sendMsg(id, msgF.replaceAll("§.", ""));
		}
	}

	public static void link(UUID player, long userID) {
		TelegramChat.data.addChatPlayerLink(userID, player);
		OfflinePlayer p = Bukkit.getOfflinePlayer(player);
		telegramHook.sendMsg(userID, "Success! Linked " + p.getName());
	}
	
	public boolean isChatLinked(Chat chat) {
		if(TelegramChat.getBackend().getLinkedChats().containsKey(chat.getId())) {
			return true;
		}
		
		return false;
	}

	public static String generateLinkToken() {

		Random rnd = new Random();
		int i = rnd.nextInt(9999999);
		String s = i + "";
		String finals = "";
		for (char m : s.toCharArray()) {
			int m2 = Integer.parseInt(m + "");
			int rndi = rnd.nextInt(2);
			if (rndi == 0) {
				m2 += 97;
				char c = (char) m2;
				finals = finals + c;
			} else {
				finals = finals + m;
			}
		}
		return finals;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if (!this.getConfig().getBoolean("enable-joinquitmessages"))
			return;

		if(isSuperVanish && VanishAPI.isInvisible(e.getPlayer()))
			return;

		if(!telegramHook.tgUsername.isEmpty()) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG("join-message", e.getPlayer().getName())[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		if (!this.getConfig().getBoolean("enable-deathmessages"))
			return;

		if(!telegramHook.tgUsername.isEmpty()) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG("death-message", e.getDeathMessage())[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onAdvancement(PlayerAdvancementDoneEvent e) {
				
		if (!this.getConfig().getBoolean("enable-advancement"))
			return;
		
		if(e.getAdvancement() == null ||
				e.getAdvancement().getKey() == null ||
				e.getAdvancement().getKey().getKey() == null ||
				!e.getAdvancement().getKey().getKey().contains("/"))
			return;
		
		String type = e.getAdvancement().getKey().getKey().split("/")[0];
		
		List<String> advancementTypes = Arrays.stream(this.getConfig().getString("advancement-types").split("\\,")) // split on comma
                .map(str -> str.trim()) // remove white-spaces
                .collect(Collectors.toList()); // collect to List
		
		if (!telegramHook.tgUsername.isEmpty() && advancementTypes.contains(type)) {
			
			String toDisplay = e.getAdvancement().getKey().getKey()
					.replace(type + "/", "")
					.replaceAll("_", " ");
			
			if(toDisplay.equals("root"))
				return;
			
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG(
					"advancement-message", 
					e.getPlayer().getDisplayName(), 
					toDisplay)[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (!this.getConfig().getBoolean("enable-joinquitmessages"))
			return;

		if(isSuperVanish && VanishAPI.isInvisible(e.getPlayer()))
			return;

		if(!telegramHook.tgUsername.isEmpty()) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils.formatMSG("quit-message", e.getPlayer().getName())[0];
			telegramHook.sendAll(chat);
		}
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent e) {
		if (!this.getConfig().getBoolean("enable-chatmessages"))
			return;
		if (e.isCancelled())
			return;
		if(!telegramHook.tgUsername.isEmpty()) {
			ChatMessageToTelegram chat = new ChatMessageToTelegram();
			chat.parse_mode = "Markdown";
			chat.text = Utils
					.escape(Utils.formatMSG("general-message-to-telegram", e.getPlayer().getName(), e.getMessage())[0])
					.replaceAll("§.", "");
			telegramHook.sendAll(chat);
		}
	}

	public static TelegramChat getInstance()
	{
		return instance;
	}
}
