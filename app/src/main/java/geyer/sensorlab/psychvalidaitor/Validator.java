package geyer.sensorlab.psychvalidaitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Validator extends Service {

    static final String TAG = "SERVICE";
    String currentTask;
    Handler handler;
    int notificationID;
    SharedPreferences prefs;
    SharedPreferences.Editor editor;

    BroadcastReceiver screenReceiver;

    NotificationManagerCompat notificationManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        currentTask = "";
        initializeSQL();
        initializeSharedPrefs();
        initializeHandler();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setUpNotificationChannels();
        }
        initializeService();
        initializeBroadcastReceivers();
        initializeOrganiser();

        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setUpNotificationChannels() {

        notificationManager = NotificationManagerCompat.from(this);

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library

        CharSequence name = "channleK";
        String description = "for validating";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel("ChannelIDMeh", name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);


        CharSequence nameTwo = "channleR";
        String descriptionTwo = "for researcher";
        int importanceTwo = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channelTwo = new NotificationChannel("ChannelIDRes", nameTwo, importanceTwo);
        channelTwo.setDescription(descriptionTwo);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManagerTwo = getSystemService(NotificationManager.class);
        notificationManagerTwo.createNotificationChannel(channelTwo);
    }

    private void initializeBroadcastReceivers() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null) {
                    switch (intent.getAction()) {
                        case "action complete":
                            notificationManager.cancel(1000);
                            switch (currentTask){
                                case "screen":
                                    Log.i(TAG, "SCREEN: " + currentTask);
                                    editor.putInt("promptOnScreen", (prefs.getInt("promptOnScreen", 0) -1)).apply();
                                    break;
                                case "uninstall":
                                    Log.i(TAG, "UNINSTALL " + currentTask);
                                    editor.putInt("promptUninstallationOfApp", (prefs.getInt("promptUninstallationOfApp", 0) -1)).apply();
                                    break;
                                case "install":
                                    Log.i(TAG, "INSTALL " + currentTask);
                                    editor.putInt("promptInstallationOfApp", (prefs.getInt("promptInstallationOfApp", 0) -1)).apply();
                                    break;
                                case "restart":
                                    Log.i(TAG, "RESTART " + currentTask);
                                    editor.putInt("restartPhone", (prefs.getInt("restartPhone", 0) -1)).apply();
                                    break;
                            }

                            storeInternally(currentTask);
                            handler.postDelayed(returnToOrganiser, 1000*3);
                            break;
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("action complete");

        registerReceiver(screenReceiver, intentFilter);
    }


    private void initializeSharedPrefs() {
        prefs = getSharedPreferences("servicePrefs", MODE_PRIVATE);
        editor = prefs.edit();

        if (!prefs.getBoolean("values recorded", false)){
            editor
                    .putInt("notificationsToSend", 10)
                    .putInt("appsToSwapTo", 20)
                    .putInt("promptOnScreen", 10)
                    .putInt("promptUninstallationOfApp",2)
                    .putInt("promptInstallationOfApp", 2)
                    .putInt("restartPhone", 1)
                    .putBoolean("values recorded", true)
                    .apply();
        }
    }

    private void initializeOrganiser() {

        ArrayList<String> actionsToPerform = new ArrayList<>();

        if(prefs.getInt("notificationsToSend",1) > 0){
            actionsToPerform.add("notification");
        }
        if(prefs.getInt("appsToSwapTo",1) > 0){
            actionsToPerform.add("swapApps");
        }
        if(prefs.getInt("promptOnScreen", 1) >  0){
            actionsToPerform.add("screen");
        }
        if(prefs.getInt("promptUninstallationOfApp",1) >0){
            actionsToPerform.add("uninstall");
        }
        if(prefs.getInt("promptInstallationOfApp",1)>0){
            actionsToPerform.add("install");
        }
        if(prefs.getInt("restartPhone",1)>0){
            actionsToPerform.add("restart");
        }

        if(actionsToPerform.size() >0){
            Random rand = new Random();
            String singleActionToPerform = actionsToPerform.get(rand.nextInt(actionsToPerform.size()));
            Log.i(TAG, "action to perform: " + singleActionToPerform);
            switch (singleActionToPerform ){
                case "notification":
                    sendNotification();
                    break;
                case "swapApps":
                    launchOfNewApp();
                    break;
                case "screen":
                    reportToResearcher("screen");
                    break;
                case "uninstall":
                    reportToResearcher("uninstall");
                    break;
                case "install":
                    reportToResearcher("install");
                    break;
                case "restart":
                    reportToResearcher("restart");
                    break;
            }
        }else{
            Log.e(TAG, "Finished");
        }

    }

    private void initializeHandler() {
        handler = new Handler();
        notificationID = 1;
    }

    private void sendNotification() {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "ChannelIDMeh")
                .setSmallIcon(R.drawable.ic_prospective_logger)
                .setContentTitle("validator")
                .setContentText("validating")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.build();

// notificationId is a unique int for each notification that you must define
        notificationManager.notify(notificationID, mBuilder.build());

        editor.putInt("notificationsToSend", (prefs.getInt("notificationsToSend", 0) -1)).apply();

        storeInternally("notification"+notificationID+ " sent");
        handler.postDelayed(killNotification, 1000*3);
    }

    private Runnable killNotification = new Runnable() {
        @Override
        public void run() {
            notificationManager.cancel(notificationID);
            storeInternally("notification"+notificationID+ " killed");
            notificationID++;
            handler.postDelayed(returnToOrganiser,1000*5);
        }
    };

    private Runnable returnToOrganiser = new Runnable() {
        @Override
        public void run() {
            initializeOrganiser();
        }
    };


    private void initializeSQL() {
        SQLiteDatabase.loadLibs(this);
    }

    private void launchOfNewApp() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        final PackageManager pm = getPackageManager();
        List<ApplicationInfo> pkgAppsList = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        Random random = new Random();
        String nextApp = pkgAppsList.get(random.nextInt(pkgAppsList.size())).packageName;

        Log.i(TAG, "App to launch: " + nextApp);

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(nextApp);
        if (launchIntent != null) {
            startActivity(launchIntent);//null pointer check in case package name was not found
            storeInternally(nextApp);
            editor.putInt("appsToSwapTo", (prefs.getInt("appsToSwapTo", 0) -1)).apply();
            handler.postDelayed(returnToOrganiser, 1000*3);
        }else{
            launchOfNewApp();
        }
    }


    private void initializeService() {
        Log.i(TAG, "running");

        if (Build.VERSION.SDK_INT >= 26) {
            if (Build.VERSION.SDK_INT > 26) {
                String CHANNEL_ONE_ID = "sensor.example. geyerk1.inspect.screenservice";
                String CHANNEL_ONE_NAME = "Screen service";
                NotificationChannel notificationChannel;
                notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                        CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_MIN);
                notificationChannel.setShowBadge(true);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                assert manager != null;
                manager.createNotificationChannel(notificationChannel);

                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_prospective_logger);
                Notification notification = new Notification.Builder(getApplicationContext())
                        .setChannelId(CHANNEL_ONE_ID)
                        .setContentTitle("Recording data")
                        .setContentText("activity logger is collecting data")
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_prospective_logger)
                        .setLargeIcon(icon)
                        .build();

                Intent notificationIntent = new Intent(getApplicationContext(),MainActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                notification.contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                startForeground(101, notification);
            } else {
                startForeground(101, updateNotification());
            }
        } else {
            Intent notificationIntent = new Intent(this, MainActivity.class);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("Recording data")
                    .setOngoing(true)
                    .setContentText("activity logger is collecting data")
                    .setContentIntent(pendingIntent).build();

            startForeground(101, notification);
        }
    }

    private Notification updateNotification() {

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        return new NotificationCompat.Builder(this)
                .setContentTitle("Recording data")
                .setContentText("activity logger is collecting data")
                .setSmallIcon(R.drawable.ic_prospective_logger)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOngoing(true).build();
    }

    private void storeInternally(String event){
        SQLiteDatabase database = ProspectiveSQL.getInstance(this).getWritableDatabase("sensorlab");

        final long time = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put(ProspectiveSQLCol.ProspectiveSQLColName.EVENT, event);
        values.put(ProspectiveSQLCol.ProspectiveSQLColName.TIME, time);

        database.insert(ProspectiveSQLCol.ProspectiveSQLColName.TABLE_NAME, null, values);

        Cursor cursor = database.rawQuery("SELECT * FROM '" + ProspectiveSQLCol.ProspectiveSQLColName.TABLE_NAME + "';", null);
        Log.d("BackgroundLogging", "Update: " + event + " " + time);
        cursor.close();
        database.close();
    }

    private void reportToResearcher(String instruction){
        Log.e("RESEARCHER", instruction);
        promptResearcher(instruction);
        currentTask = instruction;
    }

    private void promptResearcher(String instructions){
        Intent carryon = new Intent();
        carryon.setAction("action complete");
        carryon.putExtra("action", instructions);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, carryon, 0);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "ChannelIDRes")
                .setSmallIcon(R.drawable.ic_research)
                .setContentTitle("Action")
                .setContentText(instructions)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_research, "done",
                        pendingIntent);

        mBuilder.build();

// notificationId is a unique int for each notification that you must define
        notificationManager.notify(1000, mBuilder.build());
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenReceiver);
    }
}
