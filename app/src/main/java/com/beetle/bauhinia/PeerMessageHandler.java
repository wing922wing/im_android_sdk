package com.beetle.bauhinia;

import com.beetle.im.IMMessage;
import com.beetle.im.IMPeerMessageHandler;

import java.util.Date;

/**
 * Created by houxh on 14-7-22.
 */
public class PeerMessageHandler implements IMPeerMessageHandler {
    private static PeerMessageHandler instance = new PeerMessageHandler();

    public static PeerMessageHandler getInstance() {
        return instance;
    }

    public static int now() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    public boolean handleMessage(IMMessage msg) {
        PeerMessageDB db = PeerMessageDB.getInstance();
        IMessage imsg = new IMessage();
        imsg.timestamp = now();
        imsg.sender = msg.sender;
        imsg.receiver = msg.receiver;
        imsg.setContent(msg.content);
        return db.insertMessage(imsg, msg.sender);
    }

    public boolean handleMessageACK(int msgLocalID, long uid) {
        PeerMessageDB db = PeerMessageDB.getInstance();
        return db.acknowledgeMessage(msgLocalID, uid);
    }
    public boolean handleMessageRemoteACK(int msgLocalID, long uid) {
        PeerMessageDB db = PeerMessageDB.getInstance();
        return db.acknowledgeMessageFromRemote(msgLocalID, uid);
    }
    public boolean handleMessageFailure(int msgLocalID, long uid) {
        PeerMessageDB db = PeerMessageDB.getInstance();
        return db.markMessageFailure(msgLocalID, uid);
    }
}
