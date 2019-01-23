package geyer.sensorlab.psychvalidaitor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Psych validator will have to have a button that starts the actions
 * There will be a count down called before the actions begin to start.
 *
 * psych validator will have to perform this actions:
 * - switch between apps randomly 20 times
 * - send 10 notifications
 * - destroy the previously sent notifications
 *
 * prompt the researcher to:
 * - turn on and off the smartphone screen 20 times
 * - restart the phone once
 * - uninstall the application twice
 * - install applications twice
 *
 * Store a record of when the actions were performed and document when the researcher was prompted.
 * Receive the same password as the usage logger
 *
 */


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Handler handler;
    TextView status;
    int countDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeInvisibleComponents();
        initializeVisualComponents();
    }

    private void initializeInvisibleComponents() {
        handler = new Handler();
    }

    private void initializeVisualComponents() {
        countDown = 10;
        Button changeActionState = findViewById(R.id.btnStartOrStop);
        changeActionState.setOnClickListener(this);
        status = findViewById(R.id.tvStatus);

        Button reportProgress = findViewById(R.id.btnReport);
        reportProgress.setOnClickListener(this);

        Button email = findViewById(R.id.btnEmail);
        email.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnStartOrStop:
                startRecording();
                //handler.postDelayed(changeCountDown, 500);
                break;
            case R.id.btnReport:
                reportSharedPrefsOfService();
                break;
            case R.id.btnEmail:
                packageProspectiveData();
                break;
        }
    }

    private void reportSharedPrefsOfService() {
        SharedPreferences prefs = getSharedPreferences("servicePrefs", MODE_PRIVATE);
        int
                notificationsSent = (10 - prefs.getInt("notificationsToSend", 0)),
                appsToSwap = (20 - prefs.getInt("appsToSwapTo", 0)),
                promptScreenOn = (10 - prefs.getInt("promptOnScreen", 0)),
                promptUnistall = (2- prefs.getInt("promptUninstallationOfApp", 0)),
                promptInstalls = (2- prefs.getInt("promptInstallationOfApp", 0)),
                restartPhone = (1- prefs.getInt("restartPhone", 0));

        Log.i("MAIN", "notifications send: " + notificationsSent + "\n" +
                "apps swapped: " + appsToSwap + "\n" +
                "prompt screen on: " + promptScreenOn + "\n" +
                "prompt uninstalls: " + promptUnistall + "\n" +
                "prompt installs: " + promptInstalls + "\n" +
                "restart phone: " + restartPhone + "\n");
    }

    Runnable changeCountDown = new Runnable() {
        @Override
        public void run() {

            if(--countDown > 0){
                status.setTextSize(100);
                status.setText(""+countDown);
                handler.postDelayed(changeCountDown, 1000);
            }else{
                status.setTextSize(20);
                status.setText("recording");
                startRecording();
            }
        }
    };

    private void startRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, Validator.class));
        }else{
            startService(new Intent(this, Validator.class));
        }
    }

    private void packageProspectiveData() {
        SQLiteDatabase.loadLibs(this);
        //creates document
        Document document = new Document();
        //getting destination
        File path = this.getFilesDir();
        File file = new File(path, "verificationResults.pdf");
        // Location to save
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        } catch (DocumentException e) {
            Log.e("MAIN", "document exception: " + e);
        } catch (FileNotFoundException e) {
            Log.e("MAIN", "File not found exception: " + e);
        }
        try {
            if (writer != null) {
                writer.setEncryption("concretepage".getBytes(), "sensorlab".getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
            }
        } catch (DocumentException e) {
            Log.e("MAIN", "document exception: " + e);
        }
        if (writer != null) {
            writer.createXmpMetadata();
        }
        // Open to write
        document.open();


        String selectQuery = "SELECT * FROM " + ProspectiveSQLCol.ProspectiveSQLColName.TABLE_NAME;
        SQLiteDatabase db = ProspectiveSQL.getInstance(this).getReadableDatabase("sensorlab");

        Cursor c = db.rawQuery(selectQuery, null);

        int event = c.getColumnIndex(ProspectiveSQLCol.ProspectiveSQLColName.EVENT);
        int time = c.getColumnIndex(ProspectiveSQLCol.ProspectiveSQLColName.TIME);

        PdfPTable table = new PdfPTable(2);
        //attempts to add the columns
        c.moveToLast();
        int rowLength =  c.getCount();
        if(rowLength > 0){
            try {
                for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                    Log.i("pkgSQL", "event: " + event);
                    table.addCell("$£"+c.getString(event));
                    table.addCell("£$"+String.valueOf(c.getLong(time)));
                }
            } catch (Exception e) {
                Log.e("file construct", "error " + e);
            }finally{
                if(!c.isClosed()){
                    c.close();
                }
                if(db.isOpen()){
                    db.close();
                }
            }

            //add to document
            document.setPageSize(PageSize.A4);
            document.addCreationDate();
            try {
                document.add(table);
            } catch (DocumentException e) {
                Log.e("MAIN", "Document exception: " + e);
            }
            document.addAuthor("Kris");
            document.close();
        }

        
        sendEmail();
    }

    private void sendEmail() {

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");

        //getting directory for internal files
        String directory = (String.valueOf(this.getFilesDir()) + File.separator);
        Log.i("Directory", directory);
        File directoryPath = new File(directory);
        File[] filesInDirectory = directoryPath.listFiles();
        Log.d("Files", "Size: "+ filesInDirectory.length);
        for (File file : filesInDirectory) {
            Log.d("Files", "FileName:" + file.getName());
        }

        //initializing files reference
        File appDocumented = new File(directory + File.separator + "verificationResults.pdf");

        //list of files to be uploaded
        ArrayList<Uri> files = new ArrayList<>();

        //if target files are identified to exist then they are packages into the attachments of the email
        try {
            if(appDocumented.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.usagelogger.fileprovider", appDocumented));
            }

            if(files.size()>0){
                //adds the file to the intent to send multiple data points
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }else{
                Log.e("email", "no files to upload");
            }

        }
        catch (Exception e){
            Log.e("File upload error1", "Error:" + e);
        }
    }
}
