
package com.buddycloud.android.buddydroid;

import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackConfiguration;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.RelativeLayout.LayoutParams;

import com.buddycloud.android.buddydroid.provider.BuddyCloud.Roster;
import com.buddycloud.jbuddycloud.BuddycloudClient;

public class RosterActivity extends ListActivity {
	
	protected static final String TAG = "RosterActivity";
	private Cursor mBuddies;
	private Intent backgroungService;
	
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    
    	mBuddies = managedQuery(Roster.CONTENT_URI, Roster.PROJECTION_MAP, null, null, null);
    	Log.d("provider", "cursor is: " + mBuddies);
    	startManagingCursor(mBuddies);
    	setListAdapter(new RosterAdapter());

    	getContentResolver().registerContentObserver(Roster.CONTENT_URI, false, 
    		new ContentObserver(new Handler()) {

			@Override
			public void onChange(boolean selfChange) {
				Log.d(TAG, "Content Changed");
				// mBuddies.requery() causes some strange error!??
				// just close and query again as workaraound so far
				mBuddies.close();
				mBuddies = managedQuery(Roster.CONTENT_URI, Roster.PROJECTION_MAP, null, null, null);
				((RosterAdapter)getListAdapter()).notifyDataSetChanged();
				super.onChange(selfChange);
			}
		});

    	getListView().setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				((RosterAdapter)getListAdapter()).toggle(arg2);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {} 
		});
    	// getListView().setTextFilterEnabled(true);

    	backgroungService = new Intent(this, BuddycloudService.class);
    	startService(backgroungService );
    }
    

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {    
       ((RosterAdapter)getListAdapter()).toggle(position);
    }
    
    @Override
	protected void onPause() {
		stopService(backgroungService);
		super.onPause();
	}


    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.about:
/*			showDialog(ABOUT_DIALOG);*/
			break;

		case R.id.settings:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		case R.id.feedback:
			final PackageManager packageManager = getPackageManager();
	        final Intent intent = new Intent("com.xtralogic.logcollector.intent.action.SEND_LOG");
	        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
	        final boolean isInstalled = list.size() > 0;

	        if (isInstalled){
                   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                   intent.putExtra("com.xtralogic.logcollector.intent.extra.SEND_INTENT_ACTION", Intent.ACTION_SENDTO);
                   intent.putExtra("com.xtralogic.logcollector.intent.extra.DATA", Uri.parse("mailto:androiddebug@buddycloud.com"));
                   intent.putExtra("com.xtralogic.logcollector.intent.extra.ADDITIONAL_INFO", "additional infor goes here..\n");
                   intent.putExtra(Intent.EXTRA_SUBJECT, "feedback Buddydroid");

                   intent.putExtra("com.xtralogic.logcollector.intent.extra.FORMAT", "time");

                   //The log can be filtered to contain data relevant only to your app
                   /*String[] filterSpecs = new String[3];
                   filterSpecs[0] = "AndroidRuntime:E";
                   filterSpecs[1] = TAG + ":V";
                   filterSpecs[2] = "*:S";
                   intent.putExtra(EXTRA_FILTER_SPECS, filterSpecs);*/

                   startActivity(intent);
	        } else {
	            new AlertDialog.Builder(this)
	            .setTitle(getString(R.string.app_name))
	            .setIcon(android.R.drawable.ic_dialog_info)
	            .setMessage("Install the free and open source Log Collector application to collect the device log and send it to the developer.")
	            .setPositiveButton("Install", new DialogInterface.OnClickListener(){
	                public void onClick(DialogInterface dialog, int whichButton){
	                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:com.xtralogic.android.logcollector"));
	                    marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	                    startActivity(marketIntent); 
	                }
	            })
	            .setNegativeButton("Just Mail", new Dialog.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:android-debug@buddycloud.com"));
						intent.putExtra(Intent.EXTRA_SUBJECT, "feedback Buddydroid");
						intent.putExtra(Intent.EXTRA_TEXT, "additional infor goes here..\n");
						startActivity(intent); 
					}}
				)
	            .show();
	        }
			break;
		}
		return super.onOptionsItemSelected(item);
	}


	private class RosterAdapter extends BaseAdapter {
    	
    	private int mExpandedPosition;

		public Object getItem(int position) {
			mBuddies.moveToPosition(position);
			return mBuddies.getString(mBuddies.getColumnIndex(Roster.JID));
		}

		public long getItemId(int position) {
			mBuddies.moveToPosition(position);
			return mBuddies.getLong(mBuddies.getColumnIndex(Roster._ID));
		}
		
		public int getCount() {
			if (mBuddies == null) return 0;
			return mBuddies.getCount();
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			getItem(position);
			
			boolean hasNextLocation = (mBuddies.getString(mBuddies.getColumnIndex(Roster.GEOLOC_NEXT)) != null
					   && !mBuddies.getString(mBuddies.getColumnIndex(Roster.GEOLOC_NEXT)).equals("null"));
			
			String jid = mBuddies.getString(mBuddies.getColumnIndex(Roster.JID));
			jid = jid.split("[@]")[0];
			
			
			View v = (View) makeView(convertView);
			
			TextView tv = (TextView) v.findViewById(R.id.title);
			tv.setText(mBuddies.getString(mBuddies.getColumnIndex(Roster.NAME)) + " (" + jid + ")");
			
			if (position == mExpandedPosition) {
				tv = (TextView) v.findViewById(R.id.desc);
				tv.setText(mBuddies.getString(mBuddies.getColumnIndex(Roster.STATUS)));
				
				tv = (TextView) v.findViewById(R.id.loc_prev);
				tv.setText(mBuddies.getString(mBuddies.getColumnIndex(Roster.GEOLOC_PREV)));
				tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
				tv = (TextView) v.findViewById(R.id.loc_current);
				tv.setText(mBuddies.getString(mBuddies.getColumnIndex(Roster.GEOLOC)));
				tv = (TextView) v.findViewById(R.id.loc_next);
				if (hasNextLocation) tv.setText(mBuddies.getString(mBuddies.getColumnIndex(Roster.GEOLOC_NEXT)));
				else tv.setText("");
				
				v.findViewById(R.id.desc).setVisibility(View.VISIBLE);
				v.findViewById(R.id.arrow).setVisibility(View.VISIBLE);
				ImageView iv = (ImageView) v.findViewById(R.id.arrow);
				iv.setImageResource((hasNextLocation ? R.drawable.history2 : R.drawable.history1));
				
				v.findViewById(R.id.loc_prev).setVisibility(View.VISIBLE);
				v.findViewById(R.id.loc_current).setVisibility(View.VISIBLE);
				v.findViewById(R.id.loc_next).setVisibility(hasNextLocation ? View.VISIBLE : View.GONE);
			} else {
				tv = (TextView) v.findViewById(R.id.desc);
				tv.setText(mBuddies.getString(mBuddies.getColumnIndex(Roster.GEOLOC)));
				
				v.findViewById(R.id.arrow).setVisibility(View.GONE);
				v.findViewById(R.id.loc_prev).setVisibility(View.GONE);
				v.findViewById(R.id.loc_current).setVisibility(View.GONE);
				v.findViewById(R.id.loc_next).setVisibility(View.GONE);
			}
			return v;
		}
		
		
		public void toggle(int position) {
			if (mExpandedPosition != position)
				mExpandedPosition = position;
			else
				mExpandedPosition = -1;
			notifyDataSetChanged();
		}
		
		private View makeView(View convertView) {
			if (convertView != null) return convertView;
			
			LayoutInflater i = (LayoutInflater) RosterActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);
			
			return i.inflate(R.layout.roster_row, null);
		}
    }
}