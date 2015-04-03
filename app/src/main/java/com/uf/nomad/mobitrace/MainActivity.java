package com.uf.nomad.mobitrace;

import android.app.ActivityManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.uf.nomad.mobitrace.activity.ActivityUtils;
import com.uf.nomad.mobitrace.activity.MyActivityRecognitionIntentService;
import com.uf.nomad.mobitrace.database.DataBaseHelper;
import com.uf.nomad.mobitrace.wifi.WifiScanningService;


public class MainActivity extends ActionBarActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DataBaseHelper dbh = new DataBaseHelper(getBaseContext());
        dbh.getReadableDatabase();

        mResolvingError = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .build();
        //If there is a savedInstance, use those values
        updateValuesFromBundle(savedInstanceState);

        if (!isMyServiceRunning(WifiScanningService.class)) {
            Intent pushIntentWIFI = new Intent(getApplicationContext(), WifiScanningService.class);
            getApplicationContext().startService(pushIntentWIFI);
        }
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and
            // make sure that the Start Updates and Stop Updates buttons are
            // correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
                setButtonsEnabledState();
            }

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(
                        LAST_UPDATED_TIME_STRING_KEY);
            }
            updateUI();
        }
    }

    private void setButtonsEnabledState() {
        Button button1 = (Button) findViewById(R.id.startLocationUpdates);
        Button button2 = (Button) findViewById(R.id.stopLocationUpdates);
        if (button2.isEnabled()) {
            button2.setEnabled(false);
            button1.setEnabled(true);
        } else if (button1.isEnabled()) {
            button1.setEnabled(false);
            button2.setEnabled(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    protected void onResume() {
        super.onResume();
        int code = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (code == 0) {
            System.out.println("up to date");
        } else {
            GooglePlayServicesUtil.getErrorDialog(code, this, 0);
        }
//        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
//            startLocationUpdates();
//        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
//        mGoogleApiClient.disconnect();
        super.onStop();
    }


    @Override
    protected void onPause() {
        super.onPause();
//        stopLocationUpdates();
    }


    @Override
    public void onConnected(Bundle bundle) {
        TextView mtext = (TextView) findViewById(R.id.loc);

        mtext.setText("Connected to API ");

//        if (mRequestingLocationUpdates) {
//            startLocationUpdates();
//        }

        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            // Determine whether a Geocoder is available.
            if (!Geocoder.isPresent()) {
                Toast.makeText(this, R.string.no_geocoder_available,
                        Toast.LENGTH_LONG).show();
                return;
            }

//            if (mAddressRequested) {
//                startIntentService();
//            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    // The rest of this code is all about building the error dialog

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MainActivity) getActivity()).onDialogDismissed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            }
        }
    }


    private static final String STATE_RESOLVING_ERROR = "resolving_error";
    private static final String REQUESTING_LOCATION_UPDATES_KEY = "requesting_location_updates_key";
    private static final String LOCATION_KEY = "location_key";
    private static final String LAST_UPDATED_TIME_STRING_KEY = "last_updated_time_string_key";

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        //keep state if resolving error
        savedInstanceState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);

        //keep state if updating location
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);

        //call super!
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.main_activity:
                Intent intent = new Intent(this, this.getClass());
                startActivity(intent);
                return true;
            case R.id.item_list:
                ListClicked();
                return true;
            case R.id.action_settings:
                openSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void ListClicked() {
        Intent intent = new Intent(this, ItemListActivity.class);
        startActivity(intent);
    }

    public void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void getLocClicked(View view) {
        TextView mtext = (TextView) findViewById(R.id.loc);

        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation == null) {
            mtext.setText("Cannot get location");
        } else {
            mtext.setText(String.valueOf(mLastLocation.getLatitude() + " , " + String.valueOf(mLastLocation.getLongitude())));
        }
    }

    /**
     * Create the location request and set the parameters as shown in this code sample:
     */
    LocationRequest mLocationRequest;
    Location mCurrentLocation;
    String mLastUpdateTime;
    Boolean mRequestingLocationUpdates = false;


    Boolean mRequestingActivityUpdates = false;
    String mLastActivityUpdateTime;

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(100);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    public void startLocationUpdates(View view) {
        setButtonsEnabledState();
        if (!isMyServiceRunning(LocationUpdateService.class)) {
            System.out.println("Service not running");
            Context context = getApplicationContext();
            Intent pushIntent1 = new Intent(context, LocationUpdateService.class);
            context.startService(pushIntent1);
    }
//        startLocationUpdates();
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void stopLocationUpdates(View view) {
        setButtonsEnabledState();
        if (isMyServiceRunning(LocationUpdateService.class)) {
            System.out.println("Service running");
            Context context = getApplicationContext();
            Intent pushIntent1 = new Intent(context, LocationUpdateService.class);
            context.stopService(pushIntent1);
        }
//        stopLocationUpdates();
    }

    /**
     * Stops location updates (called in onPause), should not be used if app is going to collect locations in background
     */
    protected void stopLocationUpdates() {
        mRequestingLocationUpdates = false;
//        LocationServices.FusedLocationApi.removeLocationUpdates(
//                mGoogleApiClient, this);
    }

//    protected void startLocationUpdates() {
//        if (mLocationRequest == null) {
//            createLocationRequest();
//
//            Button button1 = (Button) findViewById(R.id.startLocationUpdates);
//            Button button2 = (Button) findViewById(R.id.stopLocationUpdates);
//            button2.setEnabled(false);
//            button1.setEnabled(true);
//        }
//        mRequestingLocationUpdates = true;
//        LocationServices.FusedLocationApi.requestLocationUpdates(
//                mGoogleApiClient, mLocationRequest, this);
//
//    }

//    @Override
//    public void onLocationChanged(Location location) {
//        mCurrentLocation = location;
//        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
//        updateUI();
//    }

    private void updateUI() {
        TextView periodicLoc = (TextView) findViewById(R.id.periodicLoc);
        periodicLoc.setText("Latitude: " + String.valueOf(mCurrentLocation.getLatitude()) +
                " Longitude: " + String.valueOf(mCurrentLocation.getLongitude()) +
                " Last Update Time: " + String.valueOf(mLastUpdateTime));

        SharedPreferences mPrefs = getApplicationContext().getSharedPreferences(
                ActivityUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);
        int last_activity = mPrefs.getInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_TYPE, DetectedActivity.UNKNOWN);
        mActivityOutput = getNameFromType(last_activity) + " Conf: " + mPrefs.getInt(ActivityUtils.KEY_PREVIOUS_ACTIVITY_CONFIDENCE, 0);
        displayActivityOutput();
    }

    public void startMyActivityRecognitionIntentService(View view) {
//        mActivityResultReceiver = new ActivityResultReceiver(new Handler());
        ((TextView) findViewById(R.id.periodicAct)).setText("Receiving Activity...");
        startMyActivityRecognitionIntentService();
    }

    PendingIntent callbackIntent;

    protected void startMyActivityRecognitionIntentService() {
        // Create an intent for passing to the intent service responsible for fetching the address.
        Intent intent = new Intent(this, MyActivityRecognitionIntentService.class);

        // Pass the result receiver as an extra to the service.
        //TODO ADDING ANY EXTRAS MAKES THE ACTIVITY HASRESULT RETURN FALSE! What now??
//        intent.putExtra(Constants.RECEIVER, mActivityResultReceiver);
        callbackIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);


        mRequestingActivityUpdates = true;
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient, Constants.DETECTION_INTERVAL_MILLISECONDS, callbackIntent);
    }


    public void stopActivityUpdates(View view) {
//        setButtonsEnabledState();

        stopActivityUpdates();
    }

    /**
     * Stops location updates (called in onPause), should not be used if app is going to collect locations in background
     */
    protected void stopActivityUpdates() {
        if (mRequestingActivityUpdates) {
            mRequestingActivityUpdates = false;
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, callbackIntent);
        }
    }

    public void startMyGeoCoderIntentService(View view) {
        mAddressResultReceiver = new AddressResultReceiver(new Handler());
        ((TextView) findViewById(R.id.address)).setText("Receiving Address...");
        startMyGeoCoderIntentService();
    }

    /**
     * Creates an intent, adds location data to it as an extra, and starts the intent service for
     * fetching an address.
     */
    protected void startMyGeoCoderIntentService() {
        // Create an intent for passing to the intent service responsible for fetching the address.
        Intent intent = new Intent(this, MyGeoCoderIntentService.class);

        // Pass the result receiver as an extra to the service.
        intent.putExtra(Constants.RECEIVER, mAddressResultReceiver);

        // Pass the location data as an extra to the service.
        if (mCurrentLocation == null) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mCurrentLocation);

        // Start the service. If the service isn't already running, it is instantiated and started
        // (creating a process for it if needed); if it is running then it remains running. The
        // service kills itself automatically once all intents are processed.
        startService(intent);
    }

    /**
     * Shows a toast with the given text.
     */
    protected void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    protected void displayAddressOutput() {
        TextView address = (TextView) findViewById(R.id.address);

        address.setText(mAddressOutput);
    }

    protected void displayActivityOutput() {
        TextView activity = (TextView) findViewById(R.id.periodicAct);

        activity.setText(mActivityOutput);
    }

    private AddressResultReceiver mAddressResultReceiver;
    protected String mAddressOutput;

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string
            // or an error message sent from the intent service.
            mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            displayAddressOutput();
            // Show a toast message if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT) {
                showToast(getString(R.string.address_found));
            }

        }
    }


    //    private ActivityResultReceiver mActivityResultReceiver;
    protected String mActivityOutput;

//    class ActivityResultReceiver extends ResultReceiver {
//        public ActivityResultReceiver(Handler handler) {
//            super(handler);
//        }
//
//        @Override
//        protected void onReceiveResult(int resultCode, Bundle resultData) {
//
//            // Display the address string
//            // or an error message sent from the intent service.
//            mActivityOutput = resultData.getString(Constants.RESULT_DATA_KEY);
//            displayActivityOutput();
//            // Show a toast message if an address was found.
//            if (resultCode == Constants.SUCCESS_RESULT) {
//                showToast(getString(R.string.activity_found));
//            }
//
//        }
//    }

    private String getNameFromType(int activityType) {
        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.UNKNOWN:
                return "unknown";
            case DetectedActivity.TILTING:
                return "tilting";
            case DetectedActivity.WALKING:
                return "walking";
            case DetectedActivity.RUNNING:
                return "running";
        }
        return "unknown";
    }
}
