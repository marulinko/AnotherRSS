package de.digisocken.rss_o_tweet;

import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterLoginButton;
import com.twitter.sdk.android.tweetcomposer.ComposerActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String PROJECT_LINK = "https://github.com/no-go/AnotherRSS/blob/tweeted/README.md";
    private static final String PROJECT2_LINK = "http://style64.org/c64-truetype";
    private static final String FLATTR_ID = "o6wo7q";
    private String FLATTR_LINK;

    public Context ctx;
    public static VideoView videoView;
    private BroadcastReceiver alarmReceiver;
    private WebView webView;
    private ProgressBar progressBar;
    private UiModeManager umm;

    TwitterLoginButton loginButton;
    TwitterSession session = null;
    String username = "";

    boolean fulls = false;

    static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        super.onCreateOptionsMenu(menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        /*
        MenuItem sizeItem = menu.findItem(R.id.size_info);
        File f = this.getDatabasePath(FeedHelper.DATABASE_NAME);
        long dbSize = f.length();
        sizeItem.setTitle(String.valueOf(dbSize/1024) + getString(R.string.kB_used));
        */

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String msg = getString(R.string.searching) + " " + query;
                RssOTweet.query = query;
                FeedListFragment fr = (FeedListFragment) getFragmentManager().findFragmentById(R.id.feedlist);
                fr.getLoaderManager().restartLoader(0, null, fr);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                RssOTweet.query = "";
                FeedListFragment fr = (FeedListFragment) getFragmentManager().findFragmentById(R.id.feedlist);
                fr.getLoaderManager().restartLoader(0, null, fr);
                Toast.makeText(getApplicationContext(), R.string.close_search, Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                // Toast.makeText(getApplicationContext(), R.string.start_search, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_tweet_pic:
                if (username.equals("")) {
                    loginButton.callOnClick();
                } else {
                    int numberOfCameras = Camera.getNumberOfCameras();
                    PackageManager pm = ctx.getPackageManager();
                    final boolean deviceHasCameraFlag = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);

                    if( !deviceHasCameraFlag || numberOfCameras==0 ) {
                        final Intent intent = new ComposerActivity.Builder(MainActivity.this)
                                .session(session)
                                .createIntent();
                        startActivity(intent);
                    } else {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                        }
                    }
                }
                break;
            default:
                break;
        }

        return true;
    }

    /**
     * Beinhaltet alle Start-Funktionen der App.
     * Funktionen:
     * <ul>
     *     <li>Alarm (neu) Starten</li>
     *     <li>Datenbank bereinigen (gelöschte Feeds entfernen)</li>
     *     <li>Ein BroadcastReceiver() wird registriert, um nach neuen Feeds durch den Alarm zu horchen</li>
     * </ul>
     * Außerdem wird das Icon in die ActionBar eingefügt.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(RssOTweet.TAG, "onCreate");
        ctx = this;
        loginButton = new TwitterLoginButton(this);

        Intent intent = new Intent(MainActivity.this, LogoActivity.class);
        startActivity(intent);

        try {
            FLATTR_LINK = "https://flattr.com/submit/auto?fid="+FLATTR_ID+"&url="+
                    java.net.URLEncoder.encode(PROJECT_LINK, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        setContentView(R.layout.together);
        umm = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        RssOTweet.alarm.restart(this);
        toFullscreen();

        try {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setDisplayShowHomeEnabled(true);
                ab.setHomeButtonEnabled(true);
                ab.setDisplayUseLogoEnabled(true);
                ab.setLogo(R.drawable.ic_launcher);
                ab.setElevation(0);
                ab.setTitle(" " + getString(R.string.app_name));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        loginButton.setCallback(new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                session = result.data;
                username = session.getUserName();
            }

            @Override
            public void failure(TwitterException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, getString(R.string.youNeedApiKey), Toast.LENGTH_LONG).show();
            }
        });

        session = TwitterCore.getInstance().getSessionManager().getActiveSession();
        if (session != null) {
            username = session.getUserName();
        }

        alarmReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(getString(R.string.serviceHasNews))) {
                    int countNews = intent.getIntExtra("count", 0);
                    Toast.makeText(
                            ctx,
                            getString(R.string.newFeeds) + ": " + countNews,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        };
        videoView = (VideoView) findViewById(R.id.videoView);
        webView = (WebView) findViewById(R.id.webView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(getString(R.string.serviceHasNews));
        registerReceiver(alarmReceiver, filter);
    }

    public boolean setWebView(String url) {
        if (webView == null) return false;
        webView.setWebViewClient(new MyWebClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.loadUrl(url);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        DbClear dbClear = new DbClear();
        int size;
        float fontSize;

        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        switch (item.getItemId()) {
            case R.id.action_flattr:
                Intent intentFlattr = new Intent(Intent.ACTION_VIEW, Uri.parse(FLATTR_LINK));
                startActivity(intentFlattr);
                break;
            case R.id.action_project:
                Intent intentProj= new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_LINK));
                startActivity(intentProj);
                break;
            case R.id.action_project2:
                Intent intentProj2 = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT2_LINK));
                startActivity(intentProj2);
                break;
            case R.id.action_feedsources:
                Intent intentfs = new Intent(MainActivity.this, FeedSourcesActivity.class);
                intentfs.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intentfs);
                break;
            case R.id.action_regex:
                Intent intentreg = new Intent(MainActivity.this, PrefRegexActivity.class);
                intentreg.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intentreg);
                break;
            case R.id.action_preferences:
                Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                break;
            case R.id.action_delNotifies:
                String ns = Context.NOTIFICATION_SERVICE;
                NotificationManager nMgr = (NotificationManager) ctx.getSystemService(ns);
                nMgr.cancelAll();
                break;
            case R.id.action_readedFeeds:
                dbClear.execute(R.id.action_readedFeeds);
                break;
            case R.id.action_delFeeds:
                dbClear.execute(R.id.action_delFeeds);
                break;
            case R.id.action_biggerText:
                fontSize = mPreferences.getFloat("font_size", RssOTweet.Config.DEFAULT_FONT_SIZE);
                fontSize = fontSize * 1.1f;
                mPreferences.edit().putFloat("font_size", fontSize).apply();
                break;
            case R.id.action_smallerText:
                fontSize = mPreferences.getFloat("font_size", RssOTweet.Config.DEFAULT_FONT_SIZE);
                fontSize = fontSize * 0.9f;
                if (fontSize < 3.0f) fontSize = 3.0f;
                mPreferences.edit().putFloat("font_size", fontSize).apply();
                break;
            case R.id.action_biggerImageSize:
                size = mPreferences.getInt("image_width", RssOTweet.Config.DEFAULT_MAX_IMG_WIDTH);
                size = size + 20;
                mPreferences.edit().putInt("image_width", size).apply();
                break;
            case R.id.action_smallerImageSize:
                size = mPreferences.getInt("image_width", RssOTweet.Config.DEFAULT_MAX_IMG_WIDTH);
                size = size - 10;
                if (size < 0) size = 0;
                mPreferences.edit().putInt("image_width", size).apply();
                break;
            default:
                break;
        }

        return true;
    }

    public class MyWebClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(View.VISIBLE);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setIndeterminate(false);
            progressBar.setVisibility(View.GONE);
            super.onPageFinished(view, url);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(alarmReceiver);
    }

    @Override
    protected void onPause() {
        Log.d(RssOTweet.TAG, "onPause");
        RssOTweet.withGui = false;
        fulls = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d(RssOTweet.TAG, "onResume");
        RssOTweet.withGui = true;
        toFullscreen();
        new DbExpunge().execute();

        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean night = mPreferences.getBoolean("nightmode_use", false);
        if (night) {
            int startH = mPreferences.getInt("nightmode_use_start", RssOTweet.Config.DEFAULT_NIGHT_START);
            int stopH = mPreferences.getInt("nightmode_use_stop", RssOTweet.Config.DEFAULT_NIGHT_STOP);
            if (RssOTweet.inTimeSpan(startH, stopH) && umm.getNightMode() != UiModeManager.MODE_NIGHT_YES) {
                umm.setNightMode(UiModeManager.MODE_NIGHT_YES);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
            if (!RssOTweet.inTimeSpan(startH, stopH) && umm.getNightMode() != UiModeManager.MODE_NIGHT_NO) {
                umm.setNightMode(UiModeManager.MODE_NIGHT_NO);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        } else {
            if (umm.getNightMode() == UiModeManager.MODE_NIGHT_YES) {
                umm.setNightMode(UiModeManager.MODE_NIGHT_NO);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // image preview
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

            try {
                File mTmpFile = File.createTempFile("tmp", ".png", getCacheDir());
                FileOutputStream fos = new FileOutputStream(mTmpFile);
                fos.write(bitmapdata);
                fos.flush();
                fos.close();

                Uri imgUri = Uri.fromFile(mTmpFile);

                // make image tweet
                final Intent intent = new ComposerActivity.Builder(MainActivity.this)
                        .session(session)
                        .image(imgUri)
                        .createIntent();
                startActivity(intent);

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            loginButton.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Setzt unterschiedliche Lösch-Operationen in der DB um.
     */
    private class DbClear extends AsyncTask<Integer, Void, Void> {

        @Override
        protected Void doInBackground(Integer... params) {
            ContentValues values = new ContentValues();
            String sel = FeedContract.Feeds.COLUMN_Flag + "<> ?";
            String[] selArgs = {Integer.toString(FeedContract.Flag.FAVORITE)};
            switch (params[0]) {
                case R.id.action_delFeeds:
                    values.put(FeedContract.Feeds.COLUMN_Deleted, FeedContract.Flag.DELETED);
                    getContentResolver().update(FeedContentProvider.CONTENT_URI, values, sel, selArgs);
                    break;
                case R.id.action_readedFeeds:
                    values.put(FeedContract.Feeds.COLUMN_Flag, FeedContract.Flag.READED);
                    getContentResolver().update(FeedContentProvider.CONTENT_URI, values, sel, selArgs);
                    break;
                default:
                    break;
            }
            return null;
        }
    }

    /***
     * Dient zum Beseitigen von gelöschten Feeds. Achtung! Wird nur gemacht,
     * wenn man die App auch öffnet!
     */
    private class DbExpunge extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

            SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String[] urls = mPreferences.getString("rss_url", RssOTweet.urls).split(" ");

            for (int urli=0; urli < urls.length; urli++) {
                Date date = new Date();
                c.setTime(date);
                c.add(Calendar.DAY_OF_MONTH, -1 * RssOTweet.Config.DEFAULT_expunge);
                date = c.getTime();
                String dateStr = FeedContract.dbFriendlyDate(date);

                String where = FeedContract.Feeds.COLUMN_Date + "<? and "
                        + FeedContract.Feeds.COLUMN_Deleted + "=? and "
                        + FeedContract.Feeds.COLUMN_Source + "=?";
                getContentResolver().delete(
                        FeedContentProvider.CONTENT_URI,
                        where,
                        new String[]{
                                dateStr, Integer.toString(FeedContract.Flag.DELETED),
                                // Integer.toString(RssOTweet.Source1.id)
                                Integer.toString(urli)
                        }
                );
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            new AutoDelete().execute();
        }
    }

    private class AutoDelete extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int autodeleteDays = mPreferences.getInt("autodelete", RssOTweet.Config.DEFAULT_autodelete);
            if (autodeleteDays < 1) return null;

            Date date = new Date();
            c.setTime(date);
            c.add(Calendar.DAY_OF_MONTH, -1 * autodeleteDays);
            date = c.getTime();
            String dateStr = FeedContract.dbFriendlyDate(date);

            String where = FeedContract.Feeds.COLUMN_Date + "<? and "
                    + FeedContract.Feeds.COLUMN_Flag + "<> ?";

            ContentValues values = new ContentValues();
            values.put(FeedContract.Feeds.COLUMN_Deleted, FeedContract.Flag.DELETED);

            getContentResolver().update(
                    FeedContentProvider.CONTENT_URI,
                    values,
                    where,
                    new String[]{dateStr, Integer.toString(FeedContract.Flag.FAVORITE)}
            );

            return null;
        }
    }

    public void toFullscreen() {
        if (!fulls) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            fulls = true;
        }
    }
}
