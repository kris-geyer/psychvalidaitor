package geyer.sensorlab.psychvalidaitor;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


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
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnStartOrStop:
                handler.postDelayed(changeCountDown, 500);
                break;
        }
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

    }
}
