package com.abtotest.voiptest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import org.abtollc.sdk.AbtoApplication;
import org.abtollc.sdk.AbtoPhone;
import org.abtollc.sdk.OnRegistrationListener;
import org.abtollc.utils.Log;

public class RegisterActivity extends Activity {

    private ProgressDialog dialog;
    AbtoPhone abtoPhone;

    public static String RegDomain = "";//"altalk.abtollc.com:5088";//
    public static String RegPassword = "";//401401
    public static String RegUser = "";//"401";//


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Get AbtoPhone instance
        abtoPhone = ((AbtoApplication) getApplication()).getAbtoPhone();

        Button regButton = (Button) findViewById(R.id.register_button);
        final EditText userEdit = (EditText) findViewById(R.id.login);
        final EditText passEdit = (EditText) findViewById(R.id.password);
        final EditText domainEdit = (EditText) findViewById(R.id.domain);
        final CheckBox disableReg =(CheckBox) findViewById(R.id.disable_registration);

        ///ABTO test
        userEdit.setText(RegUser);
        passEdit.setText(RegPassword);
        domainEdit.setText(RegDomain);

        regButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {

                //Show progress
                if(dialog==null)
                {
                    dialog = new ProgressDialog(RegisterActivity.this);
                    dialog.setCancelable(false);
                    dialog.setMessage("Registering...");
                    dialog.setCancelable(false);
                }

                dialog.show();

                int regTimeout = disableReg.isChecked() ? 0 : 300;

                RegUser = userEdit.getText().toString();
                RegPassword = passEdit.getText().toString();
                RegDomain = domainEdit.getText().toString();

                // Add account
                //abtoPhone.getConfig().setContactDetailsUri("");
                //abtoPhone.getConfig().setContactDetails(";token="+URLEncoder.encode("yyyy+xxx 111 <l>"));
                abtoPhone.getConfig().addAccount(RegDomain, null, RegUser, RegPassword, null, "", regTimeout, false);

                //Register
                try {
                    abtoPhone.register();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });


        // Set registration event
        abtoPhone.setRegistrationStateListener(new OnRegistrationListener() {

            public void onRegistrationFailed(long accId, int statusCode, String statusText) {

                if(dialog != null) dialog.dismiss();

                AlertDialog.Builder fail = new AlertDialog.Builder(RegisterActivity.this);
                fail.setTitle("Registration failed");
                fail.setMessage(statusCode + " - " + statusText);
                fail.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                fail.show();
            }

            public void onRegistered(long accId) {

                //Hide progress
                if(dialog != null) dialog.dismiss();

                //Unsubscribe reg events
                abtoPhone.setRegistrationStateListener(null);

                //Start main activity
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                startActivity(intent);

                //Close this activity
                finish();
            }

            @Override
            public void onUnRegistered(long accId) {
                Log.e(this.toString(), "log succ acc = " + accId);

                Toast.makeText(RegisterActivity.this, "RegisterActivity::onUnRegistered", Toast.LENGTH_SHORT).show();
            }
        }); //registration listener

    }//onCreate


    @Override
    public void onBackPressed() {
        //Exit app here
        try {
            abtoPhone.setRegistrationStateListener(null);

            //Destroy phone
            if(abtoPhone.isActive())        abtoPhone.destroy();

        } catch (RemoteException e) {
            e.printStackTrace();
        }
        super.onBackPressed();
    }

}
