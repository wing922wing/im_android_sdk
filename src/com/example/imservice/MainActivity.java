package com.example.imservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.beetle.im.IMMessage;
import com.beetle.im.IMService;
import com.beetle.im.IMServiceObserver;
import com.example.imservice.model.Contact;
import com.example.imservice.model.ContactDB;
import com.example.imservice.model.User;
import com.example.imservice.model.UserDB;
import com.google.code.p.leveldb.LevelDB;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by houxh on 14-8-8.
 */


public class MainActivity extends Activity implements IMServiceObserver, AdapterView.OnItemClickListener {
    List<Conversation> conversations;

    ListView lv;

    private final long uid = 86013635273142L;
    private final String TAG = "imservice";


    BaseAdapter adapter;
    class ConversationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return conversations.size();
        }
        @Override
        public Object getItem(int position) {
            return conversations.get(position);
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.message, null);
            } else {
                view = convertView;
            }
            TextView tv = (TextView) view.findViewById(R.id.name);
            Conversation c = conversations.get(position);
            tv.setText(c.name);

            tv = (TextView)view.findViewById(R.id.content);
            tv.setText(c.message.content.getText());
            return view;
        }
    }

    // 初始化组件
    private void initWidget() {
        lv = (ListView) findViewById(R.id.list);
        adapter = new ConversationAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(this);
    }

    //禁用返回键
    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PeerMessageDB db = PeerMessageDB.getInstance();
        db.setDir(this.getDir("peer", MODE_PRIVATE));

        ContactDB cdb = ContactDB.getInstance();
        cdb.setContentResolver(getContentResolver());
        cdb.loadContacts();

        LevelDB ldb = LevelDB.getDefaultDB();
        String dir = getFilesDir().getAbsoluteFile() + File.separator + "db";
        ldb.open(dir);

        Log.i(TAG, "start im service");
        IMService im =  IMService.getInstance();
        im.setHost("106.186.122.158");
        im.setPort(23000);
        im.setUid(this.uid);
        im.setPeerMessageHandler(PeerMessageHandler.getInstance());
        im.addObserver(this);
        im.start();

        conversations = new ArrayList<Conversation>();
        ConversationIterator iter = PeerMessageDB.getInstance().newConversationIterator();
        while (true) {
            Conversation conv = iter.next();
            if (conv == null) {
                break;
            }
            conv.name = getUserName(conv.cid);
            conversations.add(conv);
        }
        initWidget();
    }

    private String getUserName(long uid) {
        User u = UserDB.getInstance().loadUser(uid);
        Contact c = ContactDB.getInstance().loadContact(u.number);
        if (c == null) {
            return u.number.getNumber();
        } else {
            return c.displayName;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        Conversation conv = conversations.get(position);
        Log.i(TAG, "conv:" + conv.name);

        Intent intent = new Intent(this, IMActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("peer_uid", conv.cid);
        startActivity(intent);
    }

    public void onConnectState(IMService.ConnectState state) {

    }
    public void onPeerInputting(long uid) {

    }
    public void onOnlineState(long uid, boolean on) {

    }

    public void onPeerMessage(IMMessage msg) {
        Log.i(TAG, "on peer message");
        Conversation conversation = null;
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conv = conversations.get(i);
            if (conv.cid == msg.sender) {
                conversation = conv;
                break;
            }
        }

        if (conversation == null) {
            conversation = new Conversation();
            conversation.cid = msg.sender;
            conversation.name = getUserName(msg.sender);
            conversations.add(conversation);
        }

        IMessage imsg = new IMessage();
        imsg.timestamp = now();
        imsg.msgLocalID = msg.msgLocalID;
        imsg.sender = msg.sender;
        imsg.receiver = msg.receiver;
        imsg.content = new IMessage.MessageContent();
        imsg.content.raw = msg.content;
        conversation.message = imsg;

        adapter.notifyDataSetChanged();
    }

    public static int now() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    public void onPeerMessageACK(int msgLocalID, long uid) {

    }
    public void onPeerMessageRemoteACK(int msgLocalID, long uid) {

    }
    public void onPeerMessageFailure(int msgLocalID, long uid) {

    }
}