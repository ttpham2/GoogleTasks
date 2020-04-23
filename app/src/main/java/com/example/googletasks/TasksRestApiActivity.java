package com.example.googletasks;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.ListConnectionsResponse;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.model.TaskList;
import com.google.api.services.tasks.model.TaskLists;


import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity to demonstrate using the Google Sign In API with a Google API that uses the Google
 * Java Client Library rather than a Google Play services API. See {@link }
 * for how to access the People API using this method.
 *
 * In order to use this Activity you must enable the People API on your project. Visit the following
 * link and replace 'YOUR_PROJECT_ID' to enable the API:
 * https://console.developers.google.com/apis/api/people.googleapis.com/overview?project=YOUR_PROJECT_ID
 */
public class TasksRestApiActivity extends AppCompatActivity implements
        View.OnClickListener {

    private static final String TAG = "TasksRestApiActivity";
    private static final String APPLICATION_NAME = "Google Tasks API Java Quickstart";

    // Scope for reading user's contacts
    private static final String CONTACTS_SCOPE = "https://www.googleapis.com/auth/contacts.readonly";
    private static final String TASKS_SCOPE = "https://www.googleapis.com/auth/tasks";

    // Bundle key for account object
    private static final String KEY_ACCOUNT = "key_account";

    // Request codes
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_RECOVERABLE = 9002;

    // Global instance of the HTTP transport
    private static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();

    // Global instance of the JSON factory
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private GoogleSignInClient mGoogleSignInClient;

    private Account mAccount;

    private TextView mStatusTextView;
    private TextView mDetailTextView;
    private TextView mTasksTextView;
    private Spinner mTasksSpinner;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.out.println("Creating system");

        // Views
        mStatusTextView = findViewById(R.id.status);
        mDetailTextView = findViewById(R.id.detail);

        // Button listeners
        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        //findViewById(R.id.add_task_button).setOnClickListener(this);
        //findViewById(R.id.remove_task_button).setOnClickListener(this);

        // For this example we don't need the disconnect button
        findViewById(R.id.disconnect_button).setVisibility(View.GONE);
        //findViewById(R.id.add_task_button).setVisibility(View.GONE);
        //findViewById(R.id.remove_task_button).setVisibility(View.GONE);


        // Restore instance state
        if (savedInstanceState != null) {
            mAccount = savedInstanceState.getParcelable(KEY_ACCOUNT);
        }

        // Configure sign-in to request the user's ID, email address, basic profile,
        // and readonly access to contacts.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(TASKS_SCOPE))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Show a standard Google Sign In button. If your application does not rely on Google Sign
        // In for authentication you could replace this with a "Get Google Contacts" button
        // or similar.
        SignInButton signInButton = findViewById(R.id.sign_in_button);
        signInButton.setSize(SignInButton.SIZE_STANDARD);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Check if the user is already signed in and all required scopes are granted
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (GoogleSignIn.hasPermissions(account, new Scope(TASKS_SCOPE) ) && mAccount != null) {
            //updateUI(account);
            Log.w(TAG, "onStart:already signed in, starting tasks view");
            setContentView(R.layout.tasks_main);
            updateTasksUI(account);
            getTaskLists();
        } else {
            signOut();
            updateUI(null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_ACCOUNT, mAccount);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }

        // Handling a user-recoverable auth exception
        if (requestCode == RC_RECOVERABLE) {
            if (resultCode == RESULT_OK) {
                //getTaskLists();
            } else {
                Toast.makeText(this, R.string.msg_contacts_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void signIn() {
        Log.d(TAG, "signIn:signing in");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        // Signing out clears the current authentication state and resets the default user,
        // this should be used to "switch users" without fully un-linking the user's google
        // account from your application.
        mGoogleSignInClient.signOut().addOnCompleteListener(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                updateUI(null);
            }
        });
    }

    private void handleSignInResult(@NonNull Task<GoogleSignInAccount> completedTask) {
        Log.d(TAG, "handleSignInResult:" + completedTask.isSuccessful());

        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            updateUI(account);

            // Store the account from the result
            mAccount = account.getAccount();

            // Switch to a view that enables interaction with Tasks API
            setContentView(R.layout.tasks_main);
            updateTasksUI(account);
        } catch (ApiException e) {
            Log.w(TAG, "handleSignInResult:error", e);

            // Clear the local account
            mAccount = null;

            // Signed out, show unauthenticated UI.
            updateUI(null);
        }
    }

    /*private void getTasks(){

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Tasks service = new Tasks.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
        TaskLists result = service.tasklists().list()
                .setMaxResults(10L)
                .execute();
        List<TaskList> taskLists = result.getItems();
        if (taskLists == null || taskLists.isEmpty()) {
            System.out.println("No task lists found.");
        } else {
            System.out.println("Task lists:");
            for (TaskList tasklist : taskLists) {
                System.out.printf("%s (%s)\n", tasklist.getTitle(), tasklist.getId());
            }
        }
    }*/


    private void getTaskLists() {
        List<TaskList> tasklists;
        if (mAccount == null) {
            Log.w(TAG, "getTaskLists: null account");
            return;
        }

        showProgressDialog();
        new GetTaskListsTask(this).execute(mAccount);
    }
    private void getTasks(String id){
        if (mAccount == null) {
            Log.w(TAG, "getTasks: null account");
            return;
        }

        GetTaskListParams params = new GetTaskListParams(mAccount, id);

        new GetTaskListTask(this).execute(params);
    }
    protected void loadTextView(@Nullable com.google.api.services.tasks.model.Tasks tasks) {
        hideProgressDialog();

        if (tasks == null) {
            Log.d(TAG, "getTasklists:tasklists: null");
            mTasksTextView.setText(getString(R.string.tasklist_fmt, "None"));
            return;
        }

        Log.d(TAG, "getTasklists:tasklists: size=" + tasks.size());

        // Get names of all tasklists
        /*
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < tasklists.size(); i++) {
            TaskList tasklist = tasklists.get(i);
            if (tasklist.getTitle() != null) {
                msg.append(tasklist.getTitle());

                if (i < tasklists.size() - 1) {
                    msg.append(",");
                }
            }
        }
         */
        StringBuilder msg = new StringBuilder();
        for (com.google.api.services.tasks.model.Task task : tasks.getItems()) {
            if(task.getTitle() != null){
                msg.append(task.getTitle());
                msg.append("\n");
            }
        }

        // Display names
        mTasksTextView.setText(getString(R.string.tasklist_fmt, msg.toString()));
    }

    /*
    protected void loadTextView(@Nullable TaskList tasklist) {
        hideProgressDialog();

        if (tasklist == null) {
            Log.d(TAG, "getTasklists:tasklists: null");
            mTasksTextView.setText(getString(R.string.tasklist_fmt, "None"));
            return;
        }

        Log.d(TAG, "getTasklists:tasklists: size=" + tasklist.size());

        // Get names of all tasklists
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < tasklist.size(); i++) {
            //Task task = tasklist.get(i);
            Object task = tasklist.get(i);
            if (task.getTitle() != null) {
                msg.append(tasklist.getTitle());

                if (i < tasklist.size() - 1) {
                    msg.append(",");
                }
            }
        }

        // Display names
        mTasksTextView.setText(getString(R.string.tasklist_fmt, msg.toString()));
    }

     */
    protected void updateTasksSpinner(@Nullable List<TaskList> tasklists) {
        hideProgressDialog();

        if (tasklists == null) {
            Log.d(TAG, "updateTasksSpinner:tasklists: null");
            //mTasksTextView.setText(getString(R.string.tasklist_fmt, "None"));
            return;
        }

        Log.d(TAG, "updateTasksSpinner:tasklists: size=" + tasklists.size());

        // Get names of all tasklists and put in list
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < tasklists.size(); i++) {
            TaskList tasklist = tasklists.get(i);
            if (tasklist.getTitle() != null) {
                list.add(tasklist.getTitle());
                }
            }

        // Load spinner
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTasksSpinner.setAdapter(dataAdapter);
        mTasksSpinner.setPrompt("Select tasklist");
    }


    protected void onRecoverableAuthException(UserRecoverableAuthIOException recoverableException) {
        Log.w(TAG, "onRecoverableAuthException", recoverableException);
        startActivityForResult(recoverableException.getIntent(), RC_RECOVERABLE);
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
            case R.id.sign_out_button:
                signOut();
                break;
        }
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage(getString(R.string.loading));
            mProgressDialog.setIndeterminate(true);
        }

        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.hide();
        }
    }

    private void updateTasksUI(@Nullable GoogleSignInAccount account) {
        mTasksTextView = findViewById(R.id.tasksTextView);
        mTasksSpinner = findViewById(R.id.tasksSpinner);
        findViewById(R.id.add_task_button).setOnClickListener(this);
        findViewById(R.id.remove_task_button).setOnClickListener(this);
        if (account != null) {
            Log.w(TAG, "updateTaskUI:account is not null");
            findViewById(R.id.add_task_button).setVisibility(View.VISIBLE);
            findViewById(R.id.remove_task_button).setVisibility(View.VISIBLE);
            getTaskLists();
        } else {
            Log.w(TAG, "updateTaskUI:account is null");
            //mStatusTextView.setText(R.string.signed_out);
            //mDetailTextView.setText(null);

            findViewById(R.id.add_task_button).setVisibility(View.GONE);
            findViewById(R.id.remove_task_button).setVisibility(View.GONE);

            //findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            //findViewById(R.id.sign_out_and_disconnect).setVisibility(View.GONE);
        }
    }
    private void updateUI(@Nullable GoogleSignInAccount account) {
        if (account != null) {
            mStatusTextView.setText(getString(R.string.signed_in_fmt, account.getDisplayName()));

            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.sign_out_and_disconnect).setVisibility(View.VISIBLE);
        } else {
            mStatusTextView.setText(R.string.signed_out);
            mDetailTextView.setText(null);

            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_out_and_disconnect).setVisibility(View.GONE);
        }
    }

    private static class GetTaskListParams{
        Account account;
        String id;

        GetTaskListParams(Account account, String id){
            this.account = account;
            this.id = id;
        }
    }

    private static class GetTaskListTask extends AsyncTask<GetTaskListParams, Void, com.google.api.services.tasks.model.Tasks> {

        private WeakReference<TasksRestApiActivity> mActivityRef;

        public GetTaskListTask(TasksRestApiActivity activity) {
            mActivityRef = new WeakReference<>(activity);
        }

        @Override
        protected com.google.api.services.tasks.model.Tasks doInBackground(GetTaskListParams... params) {
            if (mActivityRef.get() == null) {
                return null;
            }

            Context context = mActivityRef.get().getApplicationContext();
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton(TASKS_SCOPE));
                credential.setSelectedAccount(params[0].account);

                Tasks service = new Tasks.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                        .setApplicationName("Thomas Tasks")
                        .build();
                com.google.api.services.tasks.model.Tasks result = service.tasks().list(params[0].id)
                        .execute();

                return result;

            } catch (UserRecoverableAuthIOException recoverableException) {
                if (mActivityRef.get() != null) {
                    mActivityRef.get().onRecoverableAuthException(recoverableException);
                }
            } catch (IOException e) {
                Log.w(TAG, "getContacts:exception", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(com.google.api.services.tasks.model.Tasks tasks) {
            super.onPostExecute(tasks);
            //setTextView(lists);
            if (mActivityRef.get() != null) {
                mActivityRef.get().loadTextView(tasks);
            }
        }


    }

    private static class GetTaskListsTask extends AsyncTask<Account, Void, List<TaskList>> {

        private WeakReference<TasksRestApiActivity> mActivityRef;

        public GetTaskListsTask(TasksRestApiActivity activity) {
            mActivityRef = new WeakReference<>(activity);
        }

        @Override
        protected List<TaskList> doInBackground(Account... accounts) {
            if (mActivityRef.get() == null) {
                return null;
            }

            Context context = mActivityRef.get().getApplicationContext();
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context,
                        Collections.singleton(TASKS_SCOPE));
                credential.setSelectedAccount(accounts[0]);

                Tasks service = new Tasks.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                        .setApplicationName("Thomas Tasks")
                        .build();
                TaskLists result = service.tasklists().list()
                        .setMaxResults(10L)
                        .execute();
                List<TaskList> taskLists = result.getItems();
                if(taskLists == null || taskLists.isEmpty()){
                    System.out.println("No task lists found.");
                } else{
                    System.out.println("Task lists:");
                    for(TaskList tasklist : taskLists){
                        System.out.printf("%s (%s)\n", tasklist.getTitle(), tasklist.getId());
                    }

                }
                return taskLists;

            } catch (UserRecoverableAuthIOException recoverableException) {
                if (mActivityRef.get() != null) {
                    mActivityRef.get().onRecoverableAuthException(recoverableException);
                }
            } catch (IOException e) {
                Log.w(TAG, "getContacts:exception", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(List<TaskList> lists) {
            super.onPostExecute(lists);
            //setTextView(lists);
            if (mActivityRef.get() != null) {
                //mActivityRef.get().loadTextView(lists);
                mActivityRef.get().updateTasksSpinner(lists);
            }
        }


    }

    static private void setTextView(Object obj){
        if(obj != null){
            //mTasksTextView.setText(obj.toString());
        }
    }
}
