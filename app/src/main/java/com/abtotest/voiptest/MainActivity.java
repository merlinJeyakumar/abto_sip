package com.abtotest.voiptest;

import org.abtollc.sdk.AbtoApplication;
import org.abtollc.sdk.AbtoPhone;
import org.abtollc.sdk.OnIncomingCallListener;
import org.abtollc.sdk.OnIncomingCallListener2;
import org.abtollc.sdk.OnRegistrationListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnRegistrationListener{

    private String domain;
    private AbtoPhone abtoPhone;
    private ProgressDialog dialog;
    
    private Button audioCallButton;
    private Button videoCallButton;
    private Button sendImButton;
    private EditText callUri;
    private TextView accLabel;
    int accExpire;
    public static String START_VIDEO_CALL = "START_VIDEO_CALL";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get AbtoPhone instance
        abtoPhone = ((AbtoApplication) getApplication()).getAbtoPhone();

        // Set registration event
        abtoPhone.setRegistrationStateListener(this);

        audioCallButton = (Button) findViewById(R.id.start_audio_call);
        videoCallButton = (Button) findViewById(R.id.start_video_call);
        sendImButton = (Button) findViewById(R.id.send_im);
        callUri = (EditText) findViewById(R.id.sip_number);

        int accId = (int)abtoPhone.getCurrentAccountId();
        accExpire = abtoPhone.getConfig().getAccountExpire(accId);

        accLabel = (TextView)findViewById(R.id.account_label);
        String contact = abtoPhone.getConfig().getAccount(accId).acc_id;
        contact = contact.replace("<", "");
        contact = contact.replace(">", "");

        if (accExpire == 0)  {
            accLabel.setText("Local contact: " + contact + ":" + abtoPhone.getConfig().getSipPort());
            callUri.setHint("number@domain:port");
            domain = "";
        }
        else  {
            accLabel.setText("Registered as : " + contact);
            domain = abtoPhone.getConfig().getAccountDomain(accId);
        }

        audioCallButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                startCall(false);
            }
        });
        videoCallButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                startCall(true);
            }
        });

        sendImButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                try{
                    String sipNumber = callUri.getText().toString();
                    abtoPhone.sendTextMessage(abtoPhone.getCurrentAccountId(), "手机之间测试都是好的", sipNumber);//unicode msg test
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onRegistrationFailed(long accId, int statusCode, String statusText) {

        if(dialog != null) dialog.dismiss();

        AlertDialog.Builder fail = new AlertDialog.Builder(MainActivity.this);
        fail.setTitle("Registration failed");
        fail.setMessage(statusCode + " - " + statusText);
        fail.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        fail.show();

        onUnRegistered(0);
    }

    @Override
    public void onRegistered(long accId) {
        //Toast.makeText(this, "MainActivity - onRegistered", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUnRegistered(long arg0) {

        if(dialog != null) dialog.dismiss();

        //Unsubscribe reg events
        abtoPhone.setRegistrationStateListener(null);

        //Start reg activity
        startActivity(new Intent(MainActivity.this, RegisterActivity.class));

        //Close this activity
        finish();
    }

    public void startCall(boolean bVideo)   {
        //Get phone number to dial
        String sipNumber = callUri.getText().toString();
        if(TextUtils.isEmpty(sipNumber))  return;

        if(TextUtils.isEmpty(domain) && !sipNumber.contains("@") ) {
            Toast.makeText(this, "Specify remote side address as 'number@domain:port'", Toast.LENGTH_SHORT).show();
            return;
        }

        if(!sipNumber.contains("sip:") ) sipNumber = "sip:" + sipNumber;
        if(!sipNumber.contains("@") )   sipNumber += "@" + domain;

        Intent intent = new Intent(this, ScreenAV.class);
        intent.putExtra(AbtoPhone.IS_INCOMING, false);//!
        intent.putExtra(AbtoPhone.REMOTE_CONTACT, sipNumber);
        intent.putExtra(START_VIDEO_CALL, bVideo);
        startActivity(intent);
    }


    @Override
    public void onBackPressed() {

        if(accExpire==0) {
            onUnRegistered(0);
            return;
        }

        try {
            if(dialog==null)
            {
                dialog = new ProgressDialog(this);
                dialog.setCancelable(false);
                dialog.setMessage("Unregistering...");
            }
            dialog.show();

            abtoPhone.unregister();

        } catch (RemoteException e) {
            e.printStackTrace();
            dialog.dismiss();
        }
        }
    }


