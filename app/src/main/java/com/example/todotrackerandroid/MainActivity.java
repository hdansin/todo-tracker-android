package com.example.todotrackerandroid;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.auth0.android.Auth0;
import com.auth0.android.authentication.AuthenticationAPIClient;
import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.callback.BaseCallback;
import com.auth0.android.management.ManagementException;
import com.auth0.android.management.UsersAPIClient;
import com.auth0.android.result.UserProfile;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.mongodb.lang.NonNull;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;

import org.bson.BsonValue;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private UsersAPIClient usersClient;
    private AuthenticationAPIClient authenticationAPIClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Nav
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        // Auth0
        Auth0 auth0 = new Auth0(this);
        auth0.setOIDCConformant(true);

        //Obtain the token from the Intent's extras
        String accessToken = getIntent().getStringExtra(LoginActivity.EXTRA_ACCESS_TOKEN);
        usersClient = new UsersAPIClient(auth0, accessToken);
        authenticationAPIClient = new AuthenticationAPIClient(auth0);
        getProfile(accessToken);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // User chose the "Settings" item, show the app settings UI...
                return true;

            case R.id.logOut:
                // User chose to Log Out, call logout()
                logout();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    private void getProfile(String accessToken) {
        authenticationAPIClient.userInfo(accessToken)
                .start(new BaseCallback<UserProfile, AuthenticationException>() {
                    @Override
                    public void onSuccess(UserProfile userinfo) {
                        usersClient.getProfile(userinfo.getId())
                                .start(new BaseCallback<UserProfile, ManagementException>() {
                                    @Override
                                    public void onSuccess(UserProfile profile) {
                                        // Display the user profile
                                        TextView userName = findViewById(R.id.credentials);
                                        runOnUiThread(() -> userName.setText(profile.getName()));

                                        // Connect to MongoDB via Stitch
                                        final StitchAppClient client = Stitch.getDefaultAppClient();
                                        client.getAuth().loginWithCredential(new AnonymousCredential()).addOnCompleteListener(
                                                task -> {
                                                    if (task.isSuccessful()) {
                                                        Log.d("myApp", String.format(
                                                                "logged in as user %s with provider %s",
                                                                task.getResult().getId(),
                                                                task.getResult().getLoggedInProviderType()));
                                                    } else {
                                                        Log.e("myApp", "failed to log in", task.getException());
                                                    }
                                                });

                                        // Call "getUser()" Stitch function to get user settings and task list
                                        client.callFunction("getUser", Collections.singletonList(profile.getId()), BsonValue.class)
                                                .addOnCompleteListener(task -> {
                                                    if (task.isSuccessful()) {
                                                        // Read JSON Object into Java
                                                        String userBson = task.getResult().toString();
                                                        try {
                                                            JSONObject userObject = new JSONObject(userBson);
                                                            JSONArray taskListObject = userObject.getJSONArray("task_list");
                                                            Log.d("myApp", taskListObject.getJSONObject(0).toString());

                                                            // Display the task list
                                                            for (int i = 0; i < taskListObject.length(); i++) {
                                                                // Create the task elements
                                                                ScrollView taskView = findViewById(R.id.taskView);
                                                                LinearLayout taskLayout = findViewById(R.id.taskLayout);

                                                                TextView taskBody = new TextView(getApplicationContext());
                                                                String taskBodyString = taskListObject.getJSONObject(i).getString("body");
                                                                Log.d("myApp", taskBodyString);
                                                                taskBody.setText(taskBodyString);
                                                                taskLayout.addView(taskBody);
                                                            }

                                                        }
                                                        catch (Exception e){
                                                            Log.e("myApp", "Error assigning BSON to JSON.");
                                                        }


                                                        Log.d("myApp", String.format("%s", userBson));


                                                    } else {
                                                        Log.e("myApp", "Error calling function:", task.getException());
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onFailure(ManagementException error) {
                                        // Show error

                                    }
                                });
                    }

                    @Override
                    public void onFailure(AuthenticationException error) {
                        // Show error

                    }
                });
    }

    private void logout() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_CLEAR_CREDENTIALS, true);
        startActivity(intent);
        finish();
    }
}
