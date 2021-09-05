package ru.heckzero.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.heckzero.server.user.User;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Chat {
	private static final Logger logger = LogManager.getFormatterLogger();

	public static final String anounceFileName = "conf/announcements.txt";																											//chat announce file
	public static final int CHAT_SYS_MSG_SEND_KISS = 1;
	public static final int CHAT_SYS_MSG_HAS_THROWN_MUD_AT = 2;
	public static final int CHAT_SYS_MSG_HAS_PRESENTED_FLOWERS_TO = 3;
	public static final int CHAT_SYS_MSG_HAS_BLOCKED_CHAT_FOR_PLAYER = 4;
	public static final int CHAT_SYS_MSG_SHE_HAS_BLOCKED_CHAT_FOR_PLAYER = 5;
	public static final int CHAT_SYS_MSG_SHE_HAS_THROWN_MUD_AT = 6;
	public static final int CHAT_SYS_MSG_M_OPENED_A_GIFT = 7;
	public static final int CHAT_SYS_MSG_F_OPENED_A_GIFT = 8;
	public static final int CHAT_SYS_MSG_HAS_LEFT_OFF_FIREWORKS = 9;
	public static final int CHAT_SYS_MSG_SHE_HAS_LEFT_OFF_FIREWORKS = 10;
	public static final int CHAT_SYS_MSG_PLAYER_CHAT_OFF = 11;
	public static final int CHAT_SYS_MSG_HAS_CAUGHT_VIRUS_X_FROM = 12;
	public static final int CHAT_SYS_MSG_SHE_HAS_CAUGHT_VIRUS_X_FROM = 13;
	public static final int CHAT_SYS_MSG_PLAYER_IS_TRYING_TO_LOCATE_YOU = 14;
	public static final int CHAT_SYS_MSG_PLAYER_TOSS_A_COIN = 19;
	private final User user;
	
	public Chat(User user) {
		this.user = user;
		return;
	}

	public void startChat() {
		File anounceFile = new File(anounceFileName);
		DateFormat dateFormat = new SimpleDateFormat("HH:mm");
//		updateAll();																																																	//update user for others (now with it's chat on) and send him a list of neighbors
		if (anounceFile.exists())																																												//try to read the  announcement file
			try {
				String resultXMLString = "<S t=\"" + dateFormat.format(new Date()) + " [System] " + Files.readString(Path.of(anounceFileName), Charset.defaultCharset()) + "\t10\"/> ";
				user.sendMsgChat(resultXMLString);
			}catch (IOException e) {logger.error("startChat: file read error: %s ", e.getMessage());}							//can't read file
		else
			logger.error("startChat: WARNING:: announcement file %s does not exist", anounceFileName);
		return;
	}


/*
	public void delUserFromRoom() {																											//Послать всем игрокам в комнате сообщение что игрок покинул комнату
		user.getNeighbours(HZUser.UserType.CHAT_ON).forEach(dude -> dude.sendChatCmd(String.format("<D t=\"%s\" /> ", user.getLogin())));
		return;
	}
*/
/*
	private void addUserToRoom() {																												//send every dude in the chat room about the user has arrived, or if it's status has been changed
		user.getNeighbours(HZUser.UserType.CHAT_ON).forEach(dude -> dude.sendChatCmd(String.format("<A t=\"%s/%s/%d/%s/%s/%s\"/> ", user.getParamStr(User.Params.battleid), user.getParamStr(User.Params.group), chatStatus(dude, user), user.getParam("clan"), user.getLogin(), user.getParam("level"))));
		return;
	}
*/

/*
	private int chatStatus(User forU,  User aboutU) {																						//make chat status for toU about aboutU
		int MASK_ONLINE = 1, MASK_SLEEP = 2, MASK_BANDIT = 4, MASK_CLAIM = 8, MASK_BATTLE = 16, MASK_BOT = 2048, MASK_FRIEND = 4096, MASK_WOMAN = 8192;
		int status = 0;
		String contactGroup =  getContactGroup(forU, aboutU);																															//PDA contacts group name containing aboutU or an empty string if contact is not in a group

		status |= (aboutU.getParamInt("nochat") ^ 1) * MASK_ONLINE;																													//neighbor has his chat off
		status |= aboutU.getParamInt("chatblock") > System.currentTimeMillis() / 1000L ?  MASK_SLEEP : 0;															//neighbor's chat is blocked by police (read only)
		status |= contactGroup.equalsIgnoreCase("ДРУЗЬЯ") ? MASK_FRIEND : 0;																									//check if aboutU is a friend (in group FRIENDS)
		status |= contactGroup.equalsIgnoreCase("ВРАГИ") || contactGroup.equalsIgnoreCase("АВТОНАПАДЕНИЕ") ? MASK_BANDIT : 0;			//aboutU is bad guy for a ForU
		status |= aboutU.getParamInt("pro") << 5;																																					//user's profession
		status |= aboutU.isInClaim() ?  MASK_CLAIM : 0;																																			//user is in a claim
		status |= aboutU.isInBattle() ?  MASK_BATTLE : 0;																																		//user is in a battle
		status |= aboutU.getParamInt("bot") * MASK_BOT;																																		//user is a bot
		status |= (aboutU.getParamInt("man") ^ 1) * MASK_WOMAN;																														//this is a woman (man = 0)
		return status;
	}
*/

/*
	private String getContactGroup(User u, User contact) {																					//get a group from a contact list the contact belongs to
		String list = u.getParamStr(User.Params.list);
		List<String> splitted = Arrays.asList(list.split(","));
		return StringUtils.stripStart(splitted.stream().filter(e -> e.startsWith("$") && splitted.indexOf(e) < splitted.indexOf(contact.getLogin())).reduce((st, nd) -> nd).orElse(""), "$");	//strip the very first symbol $
	}
	

	public void updateUForRoom() {																																									//add or update user to a room with a new status or remove him from a room if he went offline
		if (user.isInGame())																																															//if user online or in a battle - update him to neighbours
			addUToRoom();
		else
			delUFromRoom();
		return;
	}
	
	public void updateAll() {
		updateUForRoom();
		showMeRoom();
		return;
	}
	

	private void showMeRoom() {																																										//Отправить игроку список всех игроков в одной комнате с ним
		HZLocation locData = user.getLocation();
		String roomData = String.format("Location [%s/%s] %s", HZLocation.normalLocToLocal(user.getParam("X")), HZLocation.normalLocToLocal(user.getParam("Y")) , (!locData.getParam("name").isEmpty()) ? " | " + locData.getParam("name") : "");
		roomData += String.format("%s", user.getParamInt("Z") != 0 ? " | " + user.getHouse().get("txt") : "");																//add a house data in case a user is inside a house
		roomData += user.getParamInt("ROOM") != 0 ?  String.format(" | Room %s", user.getParam("ROOM")) : "";														//add a room data in case a user is inside some room within a house

		StringJoiner sj = new StringJoiner(",", "<R t=\"" + roomData +"\t",  "\"/>");																									//create result string
		user.getNeighbours(HZUser.UserType.IN_GAME).forEach(dude -> sj.add(String.format("%s/%s/%d/%s/%s/%s", dude.getParam("battleid"), dude.getParam("group"), chatStatus(user, dude), dude.getParam("clan"), dude.getLogin(), dude.getParam("level"))));
		user.sendChatCmd(sj.toString());
		return;
	}
	
	public void chatPost(Node xmlData) {
		ArrayList<String> privateLogins = new ArrayList<String>();
		HZUser tmpU;
		DateFormat dateFormat = new SimpleDateFormat("HH:mm");
		boolean toClan = false;
		
		if (user.getParamLong("chatblock") > System.currentTimeMillis() / 1000l) 																											//user has his chat blocked by cop
			return;
		
		String postText = (xmlData.getAttributes().getNamedItem("t") != null) ? xmlData.getAttributes().getNamedItem("t").getNodeValue() : StringUtils.EMPTY;			//t - chat message itself
		postText = StringUtils.strip(postText.replace("\"", "&quot;"));
		String [ ] words = StringUtils.split(postText);
		String resultMsg = String.format("<S t=\"%s [%s] %s\t%s\t\" />",  dateFormat.format(new Date()), user.getLogin(),  postText, user.getParam("clr"));
		
		for (int i = 0; i < words.length; i++) 
			if (words[i].equals("private") && words[i +1].matches("^\\[.*\\]$")) {																											//found private keyword in chat message 
				 String tmpLogin = words[i + 1].substring(1, words[i + 1].length() - 1);
				 if (tmpLogin.equals("clan")) {toClan = true; break;}
				 privateLogins.add(tmpLogin);
			}
		
		if (toClan) {trace("message will be send to all online clan members"); return;}
		
		if (!privateLogins.isEmpty()) {																																											//at least one private user
			if (!privateLogins.contains(user.getLogin()))																																						//if privateUsers does not contain the sender - add sender to the list
				privateLogins.add(user.getLogin());
			for (String login: privateLogins) {																																									//send a chat message to all private users
				if ((tmpU = HZUser.getUser(login)) != null && tmpU.isChatOn()) {
					tmpU.sendChatCmd(resultMsg);
				}
				else	{																																																		//send a chat sys msg back to sender
					user.sendChatCmd(makeChatSysMsg(CHAT_SYS_MSG_PLAYER_CHAT_OFF, StringUtils.EMPTY, login));											//Персонаж [login] в данный момент не может общаться в чате
				}
			}
			return;
		}
		user.sendChatCmdToNbrs(resultMsg);
		return;
	} 
	
	public String makeChatSysMsg(int code, String ... param) {																																//generate a chat system message
		DateFormat dateFormat = new SimpleDateFormat("HH:mm");
		return  String.format("<Z t=\"%s\t%d\t", dateFormat.format(new Date()), code) +  StringUtils.join(param, "\t" ) + "\"/>";
	}*/
}
