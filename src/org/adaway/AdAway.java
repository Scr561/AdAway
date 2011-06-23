package org.adaway;


//TODO: database und constant best practices lesen

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.stericson.RootTools.*;

public class AdAway extends Activity {
    private Context mContext;
    private HostsDatabase mHostsDatabase;
    
    static final String TAG = "AdAway";
    static final String LOCALHOST_IPv4 = "127.0.0.1";
    static final String HOSTNAMES_FILENAME = "hostnames.txt";
    static final String HOSTS_FILENAME = "hosts";
    static final String LINE_SEPERATOR = System.getProperty("line.separator");

    private ProgressDialog mDownloadProgressDialog;
    public static final int DIALOG_DOWNLOAD_PROGRESS = 0;

    private ProgressDialog mApplyProgressDialog;
    public static final int DIALOG_APPLY_PROGRESS = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_hosts_files:
            startActivity(new Intent(this, HostsFiles.class));
            return true;

        case R.id.menu_preferences:
            startActivity(new Intent(this, Preferences.class));
            return true;

        case R.id.menu_help: // TODO: formatting, urls etc, bugs to bla
            AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
            alertDialog.setTitle(R.string.help_title);
            alertDialog.setMessage(getString(org.adaway.R.string.help_text));
            alertDialog.setButton(getString(R.string.button_close), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int sum) {
                    // do nothing, close
                }
            });
            alertDialog.show();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mContext = this;

        RootTools.debugMode = true;

        // check for root on device
        if (!RootTools.isRootAvailable()) { // wants root: || !RootTools.isBusyboxAvailable()) {
            // su binary does not exist, raise root dialog
            showRootDialog();
        }

        // check if private hostfile exists and enable apply button
        try {
            Log.i(TAG, "try");
            FileInputStream fis = openFileInput(HOSTNAMES_FILENAME);
            fis.close();

            Button applyButton = (Button) findViewById(R.id.apply_button);
            applyButton.setEnabled(true);

        } catch (FileNotFoundException e) {
            Button applyButton = (Button) findViewById(R.id.apply_button);
            applyButton.setEnabled(false);
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, "IO Exception");
            e.printStackTrace();
        }
        
        
        
        // TEST --------------
        mHostsDatabase = new HostsDatabase(mContext);
//        mHostsDatabase.insertHostsFile("http://bla.de");
        
        mHostsDatabase.modifyEnabled(2, 1);
        
        ArrayList<String> list = mHostsDatabase.getAllEnabledHosts();
        
        Log.d(TAG, list.toString());
        
        Log.d(TAG, "after err");

    }

    public void downloadOnClick(View view) {
        new DownloadHostnameFiles().execute("http://winhelp2002.mvps.org/hosts.txt", "http://winhelp2002.mvps.org/hosts.txt");
    }

    public void applyOnClick(View view) {
        new Apply().execute();
    }

    public void revertOnClick(View view) {
        Log.d(TAG, "revert");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.revert_question));
        builder.setCancelable(false);
        builder.setPositiveButton(getString(R.string.button_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // build standard hosts file
                try {
                    FileOutputStream fos = openFileOutput(HOSTS_FILENAME, Context.MODE_PRIVATE);

                    // default localhost
                    String localhost = LOCALHOST_IPv4 + " localhost";
                    fos.write(localhost.getBytes());
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "IO Exception");
                    e.printStackTrace();
                }

                // TODO: really revert here

                AlertDialog alertDialog;
                alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setTitle(R.string.button_revert);
                alertDialog.setMessage(getString(org.adaway.R.string.revert_successfull));
                alertDialog.setButton(getString(R.string.button_close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sum) {
                        // do nothing, close
                    }
                });
                alertDialog.show();
            }
        });
        builder.setNegativeButton(getString(R.string.button_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        AlertDialog question = builder.create();
        question.show();
    }

    private void showRootDialog() {
        Dialog rootDialog = new Dialog(mContext);
        rootDialog.setContentView(R.layout.root_dialog);
        rootDialog.setTitle(R.string.no_root_title);

        // Cyanogenmod Button goes to website
        Button cyanogenmodButton = (Button) rootDialog.findViewById(R.id.cyanogenmod_button);
        cyanogenmodButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Uri uri = Uri.parse("http://www.cyanogenmod.com");
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });

        // Exit Button closes application
        Button exitButton = (Button) rootDialog.findViewById(R.id.exit_button);
        exitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // finish this activity
                finish();
            }
        });

        rootDialog.show();
    }

    private class Apply extends AsyncTask<Void, String, Boolean> {

        @Override
        protected Boolean doInBackground(Void... unused) {
            // parse hostname files
            // make one big set of all hostnames so no hostname is more than once in it
            // build hosts file based on redirection ip from prefs, default is 127.0.0.1
            // TODO: apply hosts file using roottools
            // TODO: check for enough space on internal mem

            // /system/etc/hosts
            // infos http://forum.xda-developers.com/showthread.php?t=509997

            try {
                publishProgress(getString(R.string.apply_dialog_hostnames));

                FileInputStream fis = openFileInput(HOSTNAMES_FILENAME);

                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

                // parse file using HostsParser
                HostsParser parser = new HostsParser(reader, getApplicationContext());
                HashSet<String> hostnames = parser.getHostnames();
                LinkedList<String> comments = parser.getComments();

                fis.close();

                publishProgress(getString(R.string.apply_dialog_hosts));

                // build hosts file out of it

                FileOutputStream fos = openFileOutput(HOSTS_FILENAME, Context.MODE_PRIVATE);

                // add adaway header
                String header = "# This file is auto generated by AdAway." + LINE_SEPERATOR + "# Please do not modify it directly, it will be overwritten when AdAway is applied again." + LINE_SEPERATOR + "# " + LINE_SEPERATOR + "# The following lines are comments from the used hostname files:";
                fos.write(header.getBytes());

                // write comments from other files to header
                Iterator<String> itComments = comments.iterator();
                String comment;
                while (itComments.hasNext()) {
                    comment = itComments.next();
                    comment = LINE_SEPERATOR + comment;
                    fos.write(comment.getBytes());
                }

                fos.write(LINE_SEPERATOR.getBytes());

                String redirectionIP = SharedPrefs.getRedirectionIP(getApplicationContext());

                // add "127.0.0.1 localhost" entry
                String localhost = LINE_SEPERATOR + LOCALHOST_IPv4 + " localhost";
                fos.write(localhost.getBytes());

                // write hostnames
                Iterator<String> itHostname = hostnames.iterator();
                String line;
                String hostname;
                while (itHostname.hasNext()) {
                    // Get element
                    hostname = itHostname.next();
                    // Log.d(TAG, hostname);

                    line = LINE_SEPERATOR + redirectionIP + " " + hostname;
                    fos.write(line.getBytes());
                }

                fos.close();

                publishProgress(getString(R.string.apply_dialog_apply));

            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return true;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(DIALOG_APPLY_PROGRESS);
        }

        @Override
        protected void onProgressUpdate(String... status) {
            Log.d(TAG, status[0].toString());

            mApplyProgressDialog.setMessage(status[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            Log.d(TAG, "on post exec");
            Log.d(TAG, result.toString());

            if (result) {
                removeDialog(DIALOG_APPLY_PROGRESS);
                AlertDialog alertDialog;
                alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setTitle(R.string.apply_dialog);
                alertDialog.setMessage(getString(R.string.apply_success));
                alertDialog.setButton(getString(R.string.button_close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sum) {
                        // do nothing, close
                    }
                });
                alertDialog.show();

            } else {
                removeDialog(DIALOG_APPLY_PROGRESS);
                Log.d(TAG, "problem");
                // AlertDialog alertDialog;
                // alertDialog = new AlertDialog.Builder(mContext).create();
                // alertDialog.setTitle(R.string.no_connection_title);
                // alertDialog.setMessage(getString(org.adaway.R.string.no_connection));
                // alertDialog.setButton(getString(R.string.close_button), new DialogInterface.OnClickListener() {
                // public void onClick(DialogInterface dlg, int sum) {
                // // do nothing, close
                // }
                // });
                // alertDialog.show();
            }
        }
    }

    private class DownloadHostnameFiles extends AsyncTask<String, Integer, Boolean> {
        private String currentURL;
        private int fileSize;
        private byte data[];
        private long total;
        private int count;
        private boolean messageChanged;

        public DownloadHostnameFiles() {
            messageChanged = false;
        }

        private boolean isAndroidOnline() {
            try {
                ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                return cm.getActiveNetworkInfo().isConnectedOrConnecting();
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                if (isAndroidOnline()) {
                    // output to write into
                    FileOutputStream out = openFileOutput(HOSTNAMES_FILENAME, Context.MODE_PRIVATE);

                    for (String url : urls) {
                        Log.v(TAG, "Starting downloading hostname file: " + urls[0]);

                        URL mURL = new URL(url);
                        // if (mURL.getProtocol() == "http") { // TODO: implement SSL httpsURLConnection
                        HttpURLConnection connection = (HttpURLConnection) mURL.openConnection();
                        // } else if (mURL.getProtocol() == "https") {
                        //
                        // } else {
                        // Log.e(TAG, "wrong protocol");
                        // }
                        fileSize = connection.getContentLength();

                        // TODO:
                        // long getLastModified()
                        // Returns the value of the last-modified header field.

                        connection.connect();

                        InputStream in = connection.getInputStream();
                        if (in == null) {
                            Log.e(TAG, "Stream is null");
                        }

                        data = new byte[1024];

                        total = 0;
                        count = 0;

                        currentURL = url; // for displaying in progress dialog
                        messageChanged = true; // with this, onProgressUpdate knows that the message has been set

                        while ((count = in.read(data)) != -1) {
                            total += count;
                            publishProgress((int) ((total * 100) / fileSize));
                            out.write(data, 0, count);
                        }

                        out.write(LINE_SEPERATOR.getBytes()); // add line seperator to add hostname files together in one file
                        out.flush();
                        in.close();
                        connection.disconnect();
                    }

                    out.close();

                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                System.out.println("Exception: " + e);
                return false;
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(DIALOG_DOWNLOAD_PROGRESS);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            Log.d(TAG, progress[0].toString());

            // update dialog with filename and progress
            if (messageChanged) {
                Log.d(TAG, "messageChanged");
                mDownloadProgressDialog.setMessage(getString(R.string.download_dialog) + LINE_SEPERATOR + currentURL);
                messageChanged = false;
            }
            mDownloadProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            Log.d(TAG, "on post exec");
            Log.d(TAG, result.toString());

            if (result) {
                removeDialog(DIALOG_DOWNLOAD_PROGRESS);

                // enable apply button
                Button applyButton = (Button) findViewById(R.id.apply_button);
                applyButton.setEnabled(true);
            } else {
                removeDialog(DIALOG_DOWNLOAD_PROGRESS);
                Log.d(TAG, "problem");
                AlertDialog alertDialog;
                alertDialog = new AlertDialog.Builder(mContext).create();
                alertDialog.setTitle(R.string.no_connection_title);
                alertDialog.setMessage(getString(org.adaway.R.string.no_connection));
                alertDialog.setButton(getString(R.string.button_close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dlg, int sum) {
                        // do nothing, close
                    }
                });
                alertDialog.show();
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_DOWNLOAD_PROGRESS:
            mDownloadProgressDialog = new ProgressDialog(this);
            mDownloadProgressDialog.setMessage(getString(R.string.download_dialog));
            mDownloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mDownloadProgressDialog.setCancelable(false);
            mDownloadProgressDialog.show();
            return mDownloadProgressDialog;
        case DIALOG_APPLY_PROGRESS:
            mApplyProgressDialog = new ProgressDialog(this);
            mApplyProgressDialog.setMessage(getString(R.string.apply_dialog));
            mApplyProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mApplyProgressDialog.setCancelable(false);
            mApplyProgressDialog.show();
            return mApplyProgressDialog;
        default:
            return null;
        }
    }
}