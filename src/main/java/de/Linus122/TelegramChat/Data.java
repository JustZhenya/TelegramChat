package de.Linus122.TelegramChat;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.UUID;

public class Data {
	private String token = "";
	
	// User ID : Player ID
	private HashMap<Long, UUID> linkedChats = new HashMap<Long, UUID>();
	
	// Token : Player ID
	private HashMap<String, UUID> linkCodes = new HashMap<String, UUID>();
	
	public TreeSet<Long> chat_ids = new TreeSet<Long>();
	
	private boolean firstUse = true;
	

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	// chats 
	public HashMap<Long, UUID> getLinkedChats() {
		return linkedChats;
	}

	public void setLinkedChats(HashMap<Long, UUID> linkedChats) {
		this.linkedChats = linkedChats;
	}

	public HashMap<String, UUID> getLinkCodes() {
		return linkCodes;
	}

	public void setLinkCodes(HashMap<String, UUID> linkCodes) {
		this.linkCodes = linkCodes;
	}

	public TreeSet<Long> getIds() {
		return chat_ids;
	}

	public void setIds(TreeSet<Long> ids) {
		synchronized (TelegramChat.mutex) {
			this.chat_ids = ids;
		}
	}

	public boolean isFirstUse() {
		return firstUse;
	}

	public void setFirstUse(boolean firstUse) {
		this.firstUse = firstUse;
	}

	public void addChatPlayerLink(long chatID, UUID player) {
		linkedChats.put(chatID, player);
	}

	public void addLinkCode(String code, UUID player) {
		linkCodes.put(code, player);
	}

	public UUID getUUIDFromLinkCode(String code) {
		return linkCodes.get(code);
	}

	public void removeLinkCode(String code) {
		linkCodes.remove(code);
	}

	public UUID getUUIDFromUserID(long userID) {
		return linkedChats.get(userID);
	}

}
