package com.buddycloud;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.buddycloud.IBuddycloudService;
import com.buddycloud.android.buddydroid.BCConnectionAtomListener;
import com.buddycloud.asmack.BuddycloudLocationChannelListener;
import com.buddycloud.asmack.ChannelSync;
import com.buddycloud.collect.CellListener;
import com.buddycloud.collect.NetworkListener;
import com.buddycloud.component.ComponentAdd;
import com.buddycloud.content.BuddyCloud.Roster;
import com.buddycloud.jbuddycloud.packet.BeaconLog;
import com.buddycloud.jbuddycloud.packet.ChannelFetch;
import com.googlecode.asmack.client.AsmackClientService;
import com.googlecode.asmack.connection.IXmppTransportService;

/**
 * Buddycloud service for interacting with the transport layer and the local
 * database.
 */
public class BuddycloudService extends AsmackClientService {

    /**
     * Create a new buddycloud service with the smack config from
     * res/xml/smackproviders.xml
     */
    public BuddycloudService() {
        super(R.xml.smackproviders);
    }

    /**
     * Logging tag for BuddycloudService.
     */
    static final String TAG = BuddycloudService.class.getSimpleName();

    /**
     * The cell change listener. The cell listener recognizes cell changes and
     * informs the service about new scan results.
     */
    private CellListener cellListener = null;

    /**
     * The network listener. The network listener is responsible for detecting
     * wifi cell changes.
     */
    private NetworkListener networkListener = null;

    /**
     * A short task queue for internal command needs.
     */
    private TaskQueueThread taskQueue;

    /**
     * Create a new buddycloud service, initialize the required platform
     * listeners.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        cellListener = new CellListener(this);
        networkListener = new NetworkListener(this);
        cellListener.start();
    }

    /**
     * Enforce a quick channel sync.
     */
    public void updateChannels() {
        // TODO
        // new ChannelSync(this, mConnection, getContentResolver());
    }

    /**
     * The last time we send a beacon log.
     */
    private long beaconLogTimer;

    /**
     * Internal priority tracks the minimum priority since the last beacon
     * log send.
     */
    private int internalPriority;

    /**
     * Send a beacon log. Priority is 0..10, with 0 meaning instant and
     * 10 meaning "if 3 min. have passed".
     * @param prio The priority of sending the colleciton.
     * @throws InterruptedException 
     */
    public boolean sendBeaconLog(final int prio)
        throws InterruptedException
    {
        final int priority = Math.min(internalPriority, prio);
        return taskQueue.add(new Runnable() {
            public void run() {
                long now = System.currentTimeMillis();
                long delta = now - beaconLogTimer;
                if (delta < 30000 * priority) {
                    return;
                }
                if (delta < 10000) {
                    internalPriority = priority;
                    return;
                }
                internalPriority = 10;
                beaconLogTimer = now;
                BeaconLog log = new BeaconLog();
                log.setTo("butler.buddycloud.com");
                cellListener.appendTo(log);
                networkListener.appendTo(log);
                try {
                    client.sendFromAllResources(log);
                } catch (RemoteException e) {
                    Log.w(TAG, "Could not send beacon log", e);
                }
            }
        });
    }

    /**
     * Send a single packet.
     * @param packet The packet to send.
     * @return True on success.
     * @throws InterruptedException
     * @throws RemoteException
     */
    public void send(final Packet packet)
        throws InterruptedException, RemoteException
    {
        if (packet.getFrom() == null || packet.getFrom().length() == 0) {
            client.sendFromAllResources(packet);
        } else {
            client.send(packet, packet.getFrom());
        }
    }

    /**
     * Sync a single channel.
     * @param channel The full channel path to sync.
     */
    public void updateChannel(String channel) {
        Log.d("BC", "update channel " + channel);
        Cursor cursor = getContentResolver().query(
                Roster.CONTENT_URI,
                new String[]{Roster.LAST_UPDATED},
                "jid=?",
                new String[]{channel},
                null
        );
        if (cursor.getCount() != 1) {
            Log.e("BC", "update channel " + channel + " canceled");
            cursor.close();
            return;
        }
        while (cursor.isBeforeFirst()) { cursor.moveToNext(); }
        long l = cursor.getLong(cursor.getColumnIndex(Roster.LAST_UPDATED));
        cursor.close();
        IQ iq = new ChannelFetch(channel, l);
        try {
            send(iq);
        } catch (InterruptedException e) {
            e.printStackTrace(System.err);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start the buddycloud service and the internal task queue.
     * @param intent The start intent.
     * @param startId The start id.
     */
    @Override
    public void onStart(Intent intent, int startId) {
        Log.d(TAG, " onStart");
        super.onStart(intent, startId);

        if (taskQueue == null) {
            taskQueue = new TaskQueueThread();
            taskQueue.start();
        }

        cellListener.start();
    }

    /**
     * Destroy the current buddycloud service, freeing the listeners and
     * stopping the task queue.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, " onDestroy");
        super.onDestroy();

        cellListener.stop();

        taskQueue.stopQueue();
        taskQueue = null;
    }

    /**
     * Service binding for UI related methods.
     */
    private final IBuddycloudService.Stub binder =
        new IBuddycloudService.Stub() {

            public boolean follow(String channel) throws RemoteException {
                // TODO implement
                return false;
            }

        };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTrasportServiceConnect(IXmppTransportService service) {
        try {
            service.enableFeature(
                        "http://jabber.org/protocol/geoloc");
            service.enableFeature(
                        "http://jabber.org/protocol/geoloc-next");
            service.enableFeature(
                        "http://jabber.org/protocol/geoloc-prev");
            service.enableFeature(
                        "http://jabber.org/protocol/geoloc+notify");
            service.enableFeature(
                        "http://jabber.org/protocol/geoloc-next+notify");
            service.enableFeature(
                        "http://jabber.org/protocol/geoloc-prev+notify");
            for (String jid: client.getAllAccountJids(true)) {
                Log.d("BC", "SYNC " + jid);
                new ChannelSync(client, jid,
                    getApplicationContext().getContentResolver()
                );
            }
            String jids[] = client.getAllAccountJids(false);
            for (String jid: jids) {
                ContentValues values = new ContentValues();
                values.put(Roster.SELF, 1);
                getApplicationContext().getContentResolver().update(
                    Roster.CONTENT_URI,
                    values,
                    "jid=?",
                    new String[]{"/user/" + jid + "/channel"}
                );
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTrasportServiceDisconnect(IXmppTransportService service) {
    }

    /**
     * All ids are prefixed with bc if possible, thus return bc as prefix.
     */
    @Override
    protected String getIDPrefix() {
        return "bc";
    }

    /**
     * Called just before the client starts up, used to bind the component
     * adding tool.
     */
    @Override
    protected void preClientStart() {
        new ComponentAdd(client);
        client.registerListener(new BuddycloudLocationChannelListener());
        BCConnectionAtomListener atomListener = new BCConnectionAtomListener(
                                                    getContentResolver());
        client.registerListener(atomListener);
        client.removeTransportServiceBindListener(atomListener);
    }

}