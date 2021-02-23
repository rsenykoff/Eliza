package com.hypervasivetech.bots.eliza;
import com.lotus.sametime.awareness.*;
import com.lotus.sametime.community.*;
import com.lotus.sametime.conf.*;
import com.lotus.sametime.core.comparch.*;
import com.lotus.sametime.core.constants.*;
import com.lotus.sametime.core.types.*;
import com.lotus.sametime.core.util.connection.Connection;
import com.lotus.sametime.core.util.connection.DirectSocketConnection;
import com.lotus.sametime.core.util.connection.HttpConnection;
import com.lotus.sametime.core.util.connection.HttpsConnection;
import com.lotus.sametime.im.*;

import java.util.*;

public class Eliza implements Runnable, LoginListener, ImServiceListener, ImListener  {

	CommunityService        commService;
	Thread                  engine;
	InstantMessagingService imService;
	STSession               stsession;
	ConfService             confService;

    ElizaChat       cq[];
    ElizaRespLdr    ChatLdr;
    static ElizaConjugate  ChatConj;
    boolean         _started=false;
    String          _s;
    Timer _timer;
    
    
    public Eliza(String serverName, String userId, String password) {
        _timer = new Timer();
        System.out.println("Eliza Instantiated");
		try
		{
			stsession = new STSession("ElizaBotSession");
			ChatLdr = new ElizaRespLdr();
        	ChatConj = new ElizaConjugate();
		}
		catch (DuplicateObjectException e)
		{
			System.err.println("***********ElizaBotSession already running*************");
			e.printStackTrace();
			return;
		}

		stsession.loadSemanticComponents();
		System.out.println("loaded semantic components");
		stsession.start();
		System.out.println("session started");

		commService = (CommunityService)stsession.getCompApi(CommunityService.COMP_NAME);
		commService.addLoginListener(this);
		System.out.println("added LoginListener");
//		DirectSocketConnection directConn = new DirectSocketConnection(serverName, 1533, 10000);
		Connection[] connection = new Connection[1];
//		java.net.InetAddress inetAddress = java.net.InetAddress.getByName(serverName);
//		connection[0] = new DirectSocketConnection(serverName, 1533, 100000);
		connection[0] = new HttpConnection(serverName, 1533, 20000);
//		connection[2] = new HttpsConnection(serverName, 1533, "10.81.71.37", 443, userId, password, 10000);
		commService.setConnectivity(connection);

		char[] charPass = password.toCharArray();
		commService.loginByPassword(serverName, userId, charPass);
	}

	public void loggedIn(LoginEvent e)
	{
		System.err.println("Logged In");

		imService = (InstantMessagingService)stsession.getCompApi(InstantMessagingService.COMP_NAME);
		imService.registerImType(ImTypes.IM_TYPE_CHAT);
		imService.addImServiceListener(this);
		confService = (ConfService)stsession.getCompApi(ConfService.COMP_NAME);
        commService.getLogin().changeMyStatus(new STUserStatus(STUserStatus.ST_USER_STATUS_ACTIVE_MOBILE, 0, "I am here."));

	}

	public void loggedOut(LoginEvent e)
	{
		System.err.println("Logged Out");
		System.out.println(e.getReason());
		System.out.println(e.getSource());
	}

	public void imReceived(ImEvent e)
	{
		e.getIm().addImListener(this);
		System.err.println("********Chat session started by: " + e.getIm().getPartner().getName());
		System.out.println(e.getIm().getPartner().getId().getId());
		//		String message = "Hello " + e.getIm().getPartner().getNickName();
//		message += ". What can I help you with today?";
//		System.err.println(message);
		//e.getIm().sendText(true, message);

	}

	public void dataReceived(ImEvent e)
	{
//		System.err.println("dataReceived: " + e.getDataType());
//		System.err.println("dataReceived: " + e.getDataSubType());
//		System.err.println("dataReceived: " + e.getData());
	}

	public void imClosed(ImEvent e)
	{
		System.err.println("*********Closed session for: " + e.getIm().getPartner().getName());
	}

	public void imOpened(ImEvent e)
	{

	}

	public void openImFailed(ImEvent e) {}

	public void textReceived(ImEvent e)
	{
		String userName = e.getIm().getPartner().getName();
		String q = e.getText();

		System.err.println(userName + ": " + q);
        
        _timer.schedule(new SendDataTask(e, SendDataTask.STARTSENDING), 800);
        _timer.schedule(new SendDataTask(e, SendDataTask.STOPSENDING), 3500);
        _timer.schedule(new ParseWordsTask(e, q), 3600);
	}

    


	public void serviceAvailable(AwarenessServiceEvent e) {}

	public void serviceUnavailable(AwarenessServiceEvent e) {}


	public void start() {
		if (engine == null) {
			engine = new Thread(this, "ElizaThread");
			engine.start();
		}
	}

	public void run() {
        Thread myThread = Thread.currentThread();
        	while (engine == myThread) {
			try {
        			Thread.sleep(1000);
			} catch (InterruptedException e) {}
        	}
	}

	public static void main(String[] args) {
		String serverName = args[0];
		String userID = args[1];
		String password = args[2];
		Eliza ebb = new Eliza(serverName, userID, password);
		ebb.start();
	}

	class SendDataTask extends TimerTask
    {
        private ImEvent _e;
        private int _startOrStop;
        public static final int STARTSENDING = 0;
        public static final int STOPSENDING = 1;
        public SendDataTask(ImEvent e, int startOrStop)
        {
            _e = e;
            _startOrStop = startOrStop;
        }
	    public void run()
        {
	        _e.getIm().sendData(true, 1, _startOrStop, null);
        }
    }
    
    class ParseWordsTask extends TimerTask
    {
        private String _s;
        private ImEvent _e;
        public ParseWordsTask(ImEvent e, String s)
        {
            _e = e;
            _s = s; 
        }
        
        public void run() {
            int idx=0, idxSpace=0;
            int _length=0;      // actual no of elements in set
            int _maxLength=200;  // capacity of set
            int _w;

            //list1.addItem(s_);
            //list1.makeVisible(list1.getVisibleIndex()+1);
            _s=_s.toLowerCase()+" ";
            while(_s.indexOf("'")>=0)
                _s=_s.substring(0,_s.indexOf("'"))+_s.substring(_s.indexOf("'")+1,_s.length());

            bigloop: for(_length=0; _length<_maxLength  && idx < _s.length(); _length++){
                // find end of the first token
                idxSpace=_s.indexOf(" ",idx);
                if(idxSpace == -1) idxSpace=_s.length();

               String _resp=null;
               for(int i=0;i<ElizaChat.num_chats && _resp == null;i++) {
                   _resp=ChatLdr.cq[i].converse(_s.substring(idx,_s.length()));
                   if(_resp != null) {
                       //list1.addItem(_resp);
                       //list1.makeVisible(list1.getVisibleIndex()+1);
                       _e.getIm().sendText(true, _resp);
                       System.err.println("Eliza Bot: " + _resp);
                       break bigloop;
                   }
               }
               // eat blanks
               while(_s.length() > ++idxSpace && Character.isWhitespace(_s.charAt(idxSpace)));
               idx=idxSpace;

               if(idx >= _s.length())   {
                    _resp=ChatLdr.cq[ElizaChat.num_chats-1].converse("nokeyfound");
                    _e.getIm().sendText(true, _resp);
                    System.err.println("Eliza Bot: " + _resp);
               }
            }
        }
    }
}




