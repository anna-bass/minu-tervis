package com.example.bassa.bloodpressuresynchronizer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.oauth.OAuth10aService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Random;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static com.example.bassa.bloodpressuresynchronizer.DatabaseContract.BPEntry.TABLE_NAME;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private SharedPreferences prefs;

    public static final int AUTHENTICATION_REQUEST = 1;

    public static OAuth10aService service;
    public static OAuth1RequestToken requestToken;

    private String user_id;
    private String oauth_verifier;
    private String accessTokenKey, accessTokenSecret;
    private OAuth1AccessToken accessToken;

    private static final String ENC = "UTF-8";

    private static NavigationView navigationView;
    private static TextView personalIDMessage;
    private ListView listView;

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;
    private DBCursorAdapter databaseCursorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        accessTokenKey = prefs.getString("access_token_key", "");
        accessTokenSecret = prefs.getString("access_token_secret", "");
        user_id = prefs.getString("user_id", "");

        // http://stackoverflow.com/a/40258662/5572217
        if (accessTokenKey.isEmpty() || accessTokenSecret.isEmpty() || user_id.isEmpty()) {
            Intent intent = new Intent(this, WithingsAuthenticationActivity.class);
            startActivityForResult(intent, AUTHENTICATION_REQUEST);
        } else {
            service = new ServiceBuilder()
                    .apiKey(WithingsAPI.API_KEY)
                    .apiSecret(WithingsAPI.API_SECRET)
                    .build(WithingsAPI.instance());
            accessToken = new OAuth1AccessToken(accessTokenKey, accessTokenSecret);
            initializeUI();
        }
    }

    @Override
    // http://stackoverflow.com/a/40258662/5572217
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == AUTHENTICATION_REQUEST) {
            if (resultCode == RESULT_OK) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    user_id = extras.getString("USER_ID");
                    oauth_verifier = extras.getString("VERIFIER");
                    getAccessTokenThread.execute((Object) null);
                }
            }
        }
    }

    // http://stackoverflow.com/a/40258662/5572217
    AsyncTask<Object, Object, Object> getAccessTokenThread = new AsyncTask<Object, Object, Object>() {
        @Override
        protected Object doInBackground(Object... params) {
            try {
                accessToken = service.getAccessToken(requestToken, oauth_verifier);
                accessTokenKey = accessToken.getToken();
                accessTokenSecret = accessToken.getTokenSecret();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            // authentication complete
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("access_token_key", accessTokenKey);
            editor.putString("access_token_secret", accessTokenSecret);
            editor.putString("user_id", user_id);
            editor.apply();

            initializeUI();
        };
    };

    private void initializeUI() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        dbHelper = new DatabaseHelper(MainActivity.this); // create a database helper
        db = dbHelper.getWritableDatabase(); // get the data repository in write mode

        try {

            Uri uri = signRequestAndGetURI();
            openConnectionAndGetJSON.execute(uri);

            // while network stuff is doing its thing in the background, initialize other views

            navigationView = (NavigationView) findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(this);

            personalIDMessage = (TextView) findViewById(R.id.personalIDMessage);

            // Set user's name and id to be shown in the navbar
            updateStuff(prefs, "user_first_name");
            updateStuff(prefs, "user_last_name");
            updateStuff(prefs, "user_personal_id");
            updateStuff(prefs, "notifications_on");

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_edit) {
            Intent personalInfoIntent = new Intent(this, PersonalInfoActivity.class);
            startActivity(personalInfoIntent);
        } else if (id == R.id.nav_manage) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    protected static void updateStuff(SharedPreferences shared, String key) {
        View header = navigationView.getHeaderView(0);

        if (key.equals("user_personal_id")) {
            String userID = shared.getString(key, "");
            TextView navBarSummary = (TextView) header.findViewById(R.id.navBarSummary);
            navBarSummary.setText(userID);

            if (!shared.getString("user_personal_id", "").isEmpty()) {
                // removes the annoying orange message
                personalIDMessage.setVisibility(View.GONE);
            } else {
                personalIDMessage.setVisibility(View.VISIBLE);
            }
        } else if (key.equals("user_first_name") || key.equals("user_last_name")) {
            String firstName = shared.getString("user_first_name", "");
            String lastName = shared.getString("user_last_name", "");
            TextView navBarName = (TextView) header.findViewById(R.id.navBarName);
            navBarName.setText(firstName + " " + lastName);
        } else if (key.equals("notifications_on")) {
            if (shared.getBoolean(key, false)) {
                initializeNotifications(true);
                Log.i("NOTIFICATIONS", "ON");
            } else {
                initializeNotifications(false);
                Log.i("NOTIFICATIONS", "OFF");
            }
        }
    }

    private static void initializeNotifications(boolean initialize) {

        // initialize firing notifications
        // http://stackoverflow.com/a/16871244/5572217
        // http://karanbalkar.com/2013/07/tutorial-41-using-alarmmanager-and-broadcastreceiver-in-android/

        Intent intent = new Intent(MyApplication.getAppContext(), MyReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getAppContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        AlarmManager am = (AlarmManager) MyApplication.getAppContext().getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingIntent);

        if (initialize) {
            Calendar firingCal = Calendar.getInstance();
            Calendar currentCal = Calendar.getInstance();

            firingCal.set(Calendar.HOUR_OF_DAY, 10); // At the hour you wanna fire
            firingCal.set(Calendar.MINUTE, 0); // Particular minute
            firingCal.set(Calendar.SECOND, 0);

//            Log.i("CALENDAR", new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z").format(firingCal.getTime()));
//            Log.i("CALENDAR", new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z").format(currentCal.getTime()));

            long intendedTime = firingCal.getTimeInMillis();
            long currentTime = currentCal.getTimeInMillis();

            if (intendedTime >= currentTime) { // 8.32, 9.57, etc.
                am.setRepeating(AlarmManager.RTC_WAKEUP, intendedTime, 60000*60, pendingIntent); // 60000 is one minute, 60000*60 is 60 minutes
            } else { // 18.30, 20.19
                int hours = currentCal.get(Calendar.HOUR_OF_DAY); // 18, 19

                firingCal.set(Calendar.HOUR_OF_DAY, hours + 1);

                intendedTime = firingCal.getTimeInMillis();
                am.setRepeating(AlarmManager.RTC_WAKEUP, intendedTime, 60000*60, pendingIntent);
            }
        }

    }

    // http://stackoverflow.com/a/5683362/5572217
    private String generateRandomString() {
        char[] chars = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 32; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }

    // http://stackoverflow.com/a/6457017/5572217
    private String getSignature(String url, String params) throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
        /**
         * base string has three parts, they are connected by "&":
         * 1) protocol
         * 2) URL (needs to be URLEncoded)
         * 3) parameter list (needs to be URLEncoded).
         */
        StringBuilder base = new StringBuilder();
        base.append("GET&");
        base.append(url);
        base.append("&");
        base.append(params);

        byte[] keyBytes = (WithingsAPI.API_SECRET + "&" + accessTokenSecret).getBytes(ENC);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(key);

        // encode it, base64 it, change it to string and return
        byte[] bytes = mac.doFinal(base.toString().getBytes(ENC));
        return Base64.encodeToString(bytes, Base64.DEFAULT).trim();
    }

    private Uri signRequestAndGetURI() throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
        String oauth_nonce = generateRandomString();

        String params = "action=getmeas&" +
                "oauth_consumer_key=" + WithingsAPI.API_KEY + "&" +
                "oauth_nonce=" + oauth_nonce + "&" +
                "oauth_signature_method=HMAC-SHA1&" +
                "oauth_timestamp=" + (System.currentTimeMillis() / 1000) + "&" +
                "oauth_token=" + accessTokenKey + "&" +
                "oauth_version=1.0&" +
                "userid=" + user_id;

        // generate the oauth_signature
        // http://stackoverflow.com/a/6457017/5572217
        String signature = getSignature(
                URLEncoder.encode("http://wbsapi.withings.net/measure", ENC),
                URLEncoder.encode(params, ENC)
        );

        return Uri.parse("http://wbsapi.withings.net/measure?" + params + "&oauth_signature=" + signature);
    }

    AsyncTask<Uri, Void, JSONObject> openConnectionAndGetJSON = new AsyncTask<Uri, Void, JSONObject>() {
        @Override
        protected JSONObject doInBackground(Uri... uris) throws RuntimeException {

            Uri uri = uris[0];
            try {
                URLConnection urlConnection = new URL(uri.toString()).openConnection();
                InputStream in = urlConnection.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(in));

                StringBuilder total = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    total.append(line).append('\n');
                }

                r.close();
                in.close();

                return new JSONObject(total.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        protected void onPostExecute(JSONObject json) throws RuntimeException {
            String user_personal_id = prefs.getString("user_personal_id", "");
            if (newDataAvailable(json)) {
                if (!user_personal_id.isEmpty()) {
                    try {
                        json.put("user_personal_id", user_personal_id);
                        Log.i("JSON", json.toString());
                        postDataToServer.execute(json.toString());
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                dbHelper.deleteAll(db);
                addBPDataToDB(json);
            }

            populateListViewFromDB();
            initializeDBCursorAdapterDependentViews();
        }
    };

    private boolean newDataAvailable(JSONObject json) {
        // TODO: add condition to check whether new BP data has been added for the user
        return true;
    }

    private void addBPDataToDB(JSONObject json) {
        try {

            JSONArray measuregprs = json.getJSONObject("body").getJSONArray("measuregrps");
            int len_measuregrps = measuregprs.length();

            for (int i = 0; i < len_measuregrps; i++) {

                JSONObject grp = (JSONObject) measuregprs.get(i);
                JSONArray measures = grp.getJSONArray("measures");
                int len_measures = measures.length();
                if (len_measures != 3) continue; // if the measurement is not BP, but, for example, weight or height, then do not go any further

                int dia = 0;
                int sys = 0;
                int pulse = 0;

                for (int j = 0; j < len_measures; j++) { // otherwise go through a BP entry

                    JSONObject measure = (JSONObject) measures.get(j);
                    String type = measure.getString("type");
                    int value = measure.getInt("value");

                    if (type.equals("9")) { // Diastolic Blood Pressure (mmHg)
                        dia = value;
                    } else if (type.equals("10")) { // Systolic Blood Pressure (mmHg)
                        sys = value;
                    } else if (type.equals("11")) { // Heart Pulse (bpm)
                        pulse = value;
                    }

                }

                long id = dbHelper.insert(db, sys, dia, pulse, grp.getString("date"));

            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void populateListViewFromDB() {
        // Define a projection that specifies which columns from the database
        // you will actually use after this query.
        String[] projection = {
                DatabaseContract.BPEntry._ID,
                DatabaseContract.BPEntry.COLUMN_NAME_SYS,
                DatabaseContract.BPEntry.COLUMN_NAME_DIA,
                DatabaseContract.BPEntry.COLUMN_NAME_PULSE,
                DatabaseContract.BPEntry.COLUMN_NAME_DATE
        };

        // How you want the results sorted in the resulting Cursor
        String sortOrder = DatabaseContract.BPEntry._ID + " ASC";

        Cursor c = db.query(
                TABLE_NAME,      // The table to query
                projection,                               // The columns to return
                null,                                     // The columns for the WHERE clause
                null,                                     // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder                                 // The sort order
        );

        // Create an adapter to map values from the DB to the elements in the list view
        databaseCursorAdapter = new DBCursorAdapter(this, c, 0);
        databaseCursorAdapter.notifyDataSetChanged();
    }

    private void initializeDBCursorAdapterDependentViews() {
        listView = (ListView) findViewById(R.id.listView);
        listView.setEmptyView(findViewById(R.id.emptyLabel));

        // Set the adapter for the list view
        listView.setAdapter(databaseCursorAdapter);

        final LinearLayout bottomBtns = (LinearLayout) findViewById(R.id.bottomBtns);

        final Button selectEntriesBtn = (Button) findViewById(R.id.selectEntriesBtn);
        selectEntriesBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Tell the adapter to show the checkboxes
                databaseCursorAdapter.setCheckBoxesShown(true);
                databaseCursorAdapter.notifyDataSetChanged();

                // Remove yourself
                v.setVisibility(View.GONE);

                // Add 'Cancel', 'Send to doctor' and 'Delete' buttons instead
                bottomBtns.setVisibility(View.VISIBLE);
            }
        });

        final Button cancelBtn = (Button) findViewById(R.id.cancelBtn);
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Tell the adapter to hide the checkboxes
                databaseCursorAdapter.setCheckBoxesShown(false);
                databaseCursorAdapter.notifyDataSetChanged();

                // Remove yourself
                bottomBtns.setVisibility(View.GONE);

                // Add 'Select entries' button instead
                selectEntriesBtn.setVisibility(View.VISIBLE);
            }
        });
    }

    AsyncTask<String, Void, String> postDataToServer = new AsyncTask<String, Void, String>() {

        @Override
        protected String doInBackground(String... strings) throws RuntimeException {

            String data = strings[0];
            try {
                URLConnection urlConnection = new URL("https://fake-e-health.herokuapp.com").openConnection();
                urlConnection.setDoOutput(true);
                OutputStreamWriter wr = new OutputStreamWriter(urlConnection.getOutputStream());
                wr.write(data);
                wr.flush();

                // Get the response
                BufferedReader rd = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder total = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    total.append(line).append('\n');
                }
                wr.close();
                rd.close();

                return total.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        protected void onPostExecute(String str) throws RuntimeException {
            Log.i("RESPONSE_FROM_SERVER", str);
        }

    };

}
