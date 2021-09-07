package ru.heckzero.server;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;
import ru.heckzero.server.world.Building;
import ru.heckzero.server.world.Location;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Chat {
	private static final Logger logger = LogManager.getFormatterLogger();
	private static final String announcementFileName = "conf/announcements.txt";															//chat announcement file name
	private static final DateFormat dateFormat = new SimpleDateFormat("HH:mm");

	private static final int CHAT_SYS_MSG_SEND_KISS = 1;
	private static final int CHAT_SYS_MSG_HAS_THROWN_MUD_AT = 2;
	private static final int CHAT_SYS_MSG_HAS_PRESENTED_FLOWERS_TO = 3;
	private static final int CHAT_SYS_MSG_HAS_BLOCKED_CHAT_FOR_PLAYER = 4;
	private static final int CHAT_SYS_MSG_SHE_HAS_BLOCKED_CHAT_FOR_PLAYER = 5;
	private static final int CHAT_SYS_MSG_SHE_HAS_THROWN_MUD_AT = 6;
	private static final int CHAT_SYS_MSG_M_OPENED_A_GIFT = 7;
	private static final int CHAT_SYS_MSG_F_OPENED_A_GIFT = 8;
	private static final int CHAT_SYS_MSG_HAS_LEFT_OFF_FIREWORKS = 9;
	private static final int CHAT_SYS_MSG_SHE_HAS_LEFT_OFF_FIREWORKS = 10;
	private static final int CHAT_SYS_MSG_PLAYER_CHAT_OFF = 11;
	private static final int CHAT_SYS_MSG_HAS_CAUGHT_VIRUS_X_FROM = 12;
	private static final int CHAT_SYS_MSG_SHE_HAS_CAUGHT_VIRUS_X_FROM = 13;
	private static final int CHAT_SYS_MSG_PLAYER_IS_TRYING_TO_LOCATE_YOU = 14;
	private static final int CHAT_SYS_MSG_PLAYER_TOSS_A_COIN = 19;
	private static String announcementFileContext = "Welcome to HeckZero";

	private final User user;

	static {																																//read the context of announcement file
		Path announcementFilePath = Path.of(announcementFileName);
		if (Files.isReadable(announcementFilePath))																																												//try to read the  announcement file
			try {
				announcementFileContext = Files.readString(announcementFilePath, Charset.defaultCharset());
			}catch (IOException e) {logger.error("can't read announcement file: %s: %s", announcementFileName, e.getMessage());}			//can't read an announcement file
		else
			logger.warn("announcement file %s does not exist or could not be read", announcementFileName);
	}

	public Chat(User user) {
		this.user = user;
		return;
	}

	public void start() {
		String announcement = "<S t=\"" + dateFormat.format(new Date()) + " [System] " + announcementFileContext + "\t10\"/> ";
		updateMyStatus();
		showMeRoom();
		user.sendMsgChat(announcement);
		return;
	}

	public void removeMe() {																									 		    //send every room-mate that user has left the room
		UserManager.getRoomMates(this.user).forEach(dude -> dude.sendMsgChat(String.format("<D t=\"%s\" /> ", user.getLogin())));
		return;
	}

	public void updateMyStatus() {					 																			   			//send every room-mate the user has arrived, or if it's status has been changed
		if (user.isInGame())
			UserManager.getRoomMates(this.user).forEach(dude -> dude.sendMsgChat(String.format("<A t=\"%s/%s/%d/%s/%s/%d\"/> ", user.getParamStr(User.Params.battleid), user.getParamStr(User.Params.group), chatStatus(dude, user), user.getParamStr(User.Params.clan), user.getLogin(), user.getParamInt(User.Params.level))));
		else
			removeMe();
		return;
	}

	private int chatStatus(User forU, User aboutU) {																						//make chat status for toU about aboutU
		int MASK_ONLINE = 1, MASK_SLEEP = 2, MASK_BANDIT = 4, MASK_CLAIM = 8, MASK_BATTLE = 16, MASK_BOT = 2048, MASK_FRIEND = 4096, MASK_WOMAN = 8192;
		int status = 0;

		String contactGroup =  getContactGroup(forU, aboutU);																				//PDA contacts group name containing aboutU or an empty string if contact is not in a group
		status |= (aboutU.getParamInt(User.Params.nochat) ^ 1) * MASK_ONLINE;																//neighbor has his chat on/off
		status |= aboutU.getParamInt(User.Params.chatblock) > System.currentTimeMillis() / 1000L ?  MASK_SLEEP : 0;				    		//neighbor's chat is blocked by police (read only)
		status |= contactGroup.equalsIgnoreCase("ДРУЗЬЯ") ? MASK_FRIEND : 0;																//check if aboutU is a friend (in group FRIENDS)
		status |= contactGroup.equalsIgnoreCase("ВРАГИ") || contactGroup.equalsIgnoreCase("АВТОНАПАДЕНИЕ") ? MASK_BANDIT : 0;	    		//aboutU is bad guy for ForU
		status |= aboutU.getParamInt(User.Params.pro) << 5;																					//user's profession
		status |= aboutU.isInClaim() ?  MASK_CLAIM : 0;																						//user is in a claim
		status |= aboutU.isInBattle() ?  MASK_BATTLE : 0;																					//user is in a battle
		status |= aboutU.getParamInt(User.Params.bot) * MASK_BOT;																			//user is a bot
		status |= (aboutU.getParamInt(User.Params.man) ^ 1) * MASK_WOMAN;																	//this is a woman (man = 0)
		return status;
	}

	private String getContactGroup(User u, User contact) {																					//get a group from a contact list the contact belongs to
		String list = u.getParamStr(User.Params.list);
		List<String> splitted = Arrays.asList(list.split(","));
		return StringUtils.stripStart(splitted.stream().filter(e -> e.startsWith("$") && splitted.indexOf(e) < splitted.indexOf(contact.getLogin())).reduce((st, nd) -> nd).orElse(""), "$");	//strip the very first symbol $
	}


	public void showMeRoom() {																																										//Отправить игроку список всех игроков в одной комнате с ним
		Location locData = user.getLocation();
		String roomData = String.format("Location [%d/%d] %s", locData.getLocalX(), locData.getLocalY() , (!locData.getParamStr(Location.Params.name).isEmpty()) ? " | " + locData.getParamStr(Location.Params.name) : "");

		roomData += String.format("%s", user.getParamInt(User.Params.Z) != 0 ? " | " + locData.getBuilding(user.getParamInt(User.Params.Z)).getParamStr(Building.Params.txt) : "");	//add a house data in case a user is inside a house
		roomData += user.getParamInt(User.Params.ROOM) != 0 ?  String.format(" | Room %d", user.getParamInt(User.Params.ROOM)) : "";												//add a room data in case a user is inside some room within a house

		StringJoiner sj = new StringJoiner(",", "<R t=\"" + roomData +"\t",  "\"/>");																								//create result string
		UserManager.getRoomMates(this.user).forEach(dude -> sj.add(String.format("%s/%s/%d/%s/%s/%d", dude.getParamStr(User.Params.battleid), dude.getParamStr(User.Params.group), chatStatus(user, dude), dude.getParamStr(User.Params.clan), dude.getLogin(), dude.getParamInt(User.Params.level))));
		user.sendMsgChat(sj.toString());
		return;
	}

	public void post(String postText) {
		ArrayList<String> privateLogins = new ArrayList<String>();
		boolean toClan = false;

		if (user.getParamLong(User.Params.chatblock) > System.currentTimeMillis() / 1000l) 													//user has his chat blocked by cop
			return;
		
		postText = StringUtils.strip(postText.replace("\"", "&quot;"));																		//massage the post text
		String [ ] words = StringUtils.split(postText);
		String resultMsg = String.format("<S t=\"%s [%s] %s\t%s\t\" />", dateFormat.format(new Date()), user.getLogin(), postText, user.getParamStr(User.Params.clr));
		
		for (int i = 0; i < words.length; i++) 
			if (words[i].equals("private") && words[i +1].matches("^\\[.*\\]$")) {															//found private keyword in chat message
				 String tmpLogin = words[i + 1].substring(1, words[i + 1].length() - 1);
				 if (tmpLogin.equals("clan")) {
					 toClan = true;
					 break;
				 }
				 privateLogins.add(tmpLogin);
			}
		
		if (toClan) {																														//message be sent to all online clan members
			UserManager.getClanMatesOnline(user).forEach(u -> u.sendMsgChat(resultMsg));
			return;
		}
		
		if (!privateLogins.isEmpty()) {																										//we've got at least one private user to send a message to
			if (!privateLogins.contains(user.getLogin()))																					//if privateUsers does not contain the sender - add sender to the list
				privateLogins.add(user.getLogin());

			for (String login: privateLogins) {																								//send a chat private message to all collected users
				User recipient = UserManager.getOnlineUserGame(login);
				if (recipient.isOnlineChat())
					recipient.sendMsgChat(resultMsg);
				else																														//send a chat sys msg back to sender
					user.sendMsgChat(makeSysMsg(CHAT_SYS_MSG_PLAYER_CHAT_OFF, StringUtils.EMPTY, login));									//Персонаж [login] в данный момент не может общаться в чате
			}
			return;
		}
		UserManager.getRoomMates(user).forEach(u -> u.sendMsgChat(resultMsg));
		return;
	} 

	public String makeSysMsg(int code, String ... param) {																																//generate a chat system message
		return  String.format("<Z t=\"%s\t%d\t", dateFormat.format(new Date()), code) +  StringUtils.join(param, "\t" ) + "\"/>";
	}
}
