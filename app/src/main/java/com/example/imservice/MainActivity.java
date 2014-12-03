package com.example.imservice;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.beetle.im.IMMessage;
import com.beetle.im.IMService;
import com.beetle.im.IMServiceObserver;
import com.beetle.im.Timer;
import com.example.imservice.api.IMHttp;
import com.example.imservice.api.IMHttpFactory;
import com.example.imservice.api.body.PostPhone;
import com.example.imservice.formatter.MessageFormatter;
import com.example.imservice.model.Contact;
import com.example.imservice.model.ContactDB;
import com.example.imservice.model.PhoneNumber;
import com.example.imservice.api.types.User;
import com.example.imservice.model.UserDB;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * Created by houxh on 14-8-8.
 */


public class MainActivity extends Activity implements IMServiceObserver, AdapterView.OnItemClickListener, ContactDB.ContactObserver {
    List<Conversation> conversations;

    ListView lv;

    private long uid;
    private static final String TAG = "imservice";

    private Timer refreshTimer;

    private ActionBar actionBar;
    private BaseAdapter adapter;
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
            tv.setText(MessageFormatter.messageContentToString(c.message.content));
            return view;
        }
    }

    // 初始化组件
    private void initWidget() {
        actionBar=getActionBar();
        actionBar.show();

        lv = (ListView) findViewById(R.id.list);
        adapter = new ConversationAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem newItem=menu.add(0,0,0,"new");
        newItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = new Intent(MainActivity.this, NewConversation.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            }
        });
        newItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ContactDB.getInstance().loadContacts();
        ContactDB.getInstance().addObserver(this);
        this.uid = Token.getInstance().uid;
        Log.i(TAG, "start im service");
        IMService im =  IMService.getInstance();
        im.addObserver(this);

        refreshConversations();
        initWidget();

        this.refreshTimer = new Timer() {
            @Override
            protected  void fire() {
                MainActivity.this.refreshUsers();
            }
        };
        this.refreshTimer.setTimer(1000*1, 1000*3600);
        this.refreshTimer.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ContactDB.getInstance().removeObserver(this);
        IMService im =  IMService.getInstance();
        im.removeObserver(this);
        this.refreshTimer.suspend();
        Log.i(TAG, "main activity destroyed");
    }

    @Override
    public void OnExternalChange() {
        Log.i(TAG, "contactdb changed");

        for (Conversation conv : conversations) {
            conv.name = getUserName(conv.cid);
        }
        adapter.notifyDataSetChanged();

        refreshUsers();
    }

    void refreshUsers() {
        Log.i(TAG, "refresh user...");
        final ArrayList<Contact> contacts = ContactDB.getInstance().copyContacts();

        List<PostPhone> phoneList = new ArrayList<PostPhone>();
        HashSet<String> sets = new HashSet<String>();
        for (Contact contact : contacts) {
            if (contact.phoneNumbers != null && contact.phoneNumbers.size() > 0) {
                for (Contact.ContactData contactData : contact.phoneNumbers) {
                    PhoneNumber n = new PhoneNumber();
                    if (!n.parsePhoneNumber(contactData.value)) {
                        continue;
                    }
                    if (sets.contains(n.getZoneNumber())) {
                        continue;
                    }
                    sets.add(n.getZoneNumber());

                    PostPhone phone = new PostPhone();
                    phone.number = n.getNumber();
                    phone.zone = n.getZone();
                    phoneList.add(phone);
                }
            }
        }
        IMHttp imHttp = IMHttpFactory.Singleton();
        imHttp.postUsers(phoneList)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<ArrayList<User>>() {
                    @Override
                    public void call(ArrayList<User> users) {
                        UserDB userDB = UserDB.getInstance();
                        for (int i = 0; i < users.size(); i++) {
                            userDB.addUser(users.get(i));
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.e(TAG, throwable.getMessage());
                    }
                });
    }

    void refreshConversations() {
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
    }

    private String getUserName(long uid) {
        User u = UserDB.getInstance().loadUser(uid);
        Contact c = ContactDB.getInstance().loadContact(new PhoneNumber(u.zone, u.number));
        if (c == null) {
            return u.number;
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
        imsg.setContent(msg.content);
        conversation.message = imsg;

        adapter.notifyDataSetChanged();

    }

    public static int now() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    public void onPeerMessageACK(int msgLocalID, long uid) {
        Log.i(TAG, "message ack on main");
    }
    public void onPeerMessageRemoteACK(int msgLocalID, long uid) {
    }
    public void onPeerMessageFailure(int msgLocalID, long uid) {
    }
}