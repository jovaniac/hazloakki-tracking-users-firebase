package com.vinayak.geotesting;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mahmud.geotesting.R;

import java.util.Random;

public class GeoService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private DatabaseReference ref;
    //CollectionReference ref;
    private GeoFire geoFire;
    private LocationChangeListener mLocationChangeListener;
    private static final String TAG = GeoService.class.getSimpleName();

    private static int UPDATE_INTERVAL = 1000;
    private static int FATEST_INTERVAL = 1000;
    private static int DISPLACEMENT = 1;



    // Is the service tracking time?
    private boolean isServiceRunning;

    // Foreground notification id
    private static final int NOTIFICATION_ID = 1;

    // Service binder
    private final IBinder serviceBinder = new RunServiceBinder();
    private GeoQuery geoQuery;

    public class RunServiceBinder extends Binder {
        GeoService getService() {
            return GeoService.this;
        }
    }

    @Override
    public void onCreate() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Creating service");
        }
        Toast.makeText(this, "Creating service.", Toast.LENGTH_SHORT).show();

        ref = FirebaseDatabase.getInstance().getReference("users-tracking");
        //ref = FirebaseFirestore.getInstance().collection("users-tracking");
        geoFire = new GeoFire(ref);
        isServiceRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Starting service");
            Toast.makeText(this, "Starting service.", Toast.LENGTH_SHORT).show();
        }
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Binding service");
        }
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Destroying service");
        }
    }

    /**
     * Starts the timer
     */
    public void startService(LatLng latLng, double radius) {
        if (!isServiceRunning) {
            isServiceRunning = true;

        } else {
            Log.e(TAG, "startTimer request for an already running timer");
            Toast.makeText(this, "startTimer request for an already running timer", Toast.LENGTH_SHORT).show();

        }
        if (geoQuery!=null){
            geoQuery.removeAllListeners();
        }
        geoQuery = geoFire.queryAtLocation(new GeoLocation(latLng.latitude, latLng.longitude), 2f);

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                sendNotification("MRF", String.format("%s entered the dangerous area", key));
            }

            @Override
            public void onKeyExited(String key) {
                sendNotification("MRF", String.format("%s exit the dangerous area", key));
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d("MOVE", String.format("%s move within the dangerous area [%f/%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
                Log.d("ERROR", "" + error);
            }
        });

       /* geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                sendNotification("MRF", String.format("%s entered the dangerous area", key));
                System.out.println(String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onKeyExited(String key) {
                sendNotification("MRF", String.format("%s exit the dangerous area", key));
                System.out.println(String.format("Key %s is no longer in the search area", key));
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                Log.d("MOVE", String.format("%s move within the dangerous area [%f/%f]", key, location.latitude, location.longitude));
                System.out.println(String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
            }

            @Override
            public void onGeoQueryReady() {
                System.out.println("All initial data has been loaded and events have been fired!");
            }

            @Override
            public void onGeoQueryError(Exception exception) {
                Log.d("ERROR", "" + exception.toString());
                System.err.println("There was an error with this query: " + exception.toString());
            }
        });
        */
    }

    /**
     * Stops the timer
     */
    public void stopService() {
        if (isServiceRunning) {
            isServiceRunning = false;
            geoQuery.removeAllListeners();
        } else {
            Log.e(TAG, "stopTimer request for a timer that isn't running");
        }
    }

    /**
     * @return whether the timer is running
     */
    public boolean isServiceRunning() {
        return isServiceRunning;
    }

    /**
     * Returns the  elapsed time
     *
     * @return the elapsed time in seconds
     */


    /**
     * Place the service into the foreground
     */
    public void foreground() {
        startForeground(NOTIFICATION_ID, createNotification());
    }

    /**
     * Return the service to the background
     */
    public void background() {
        stopForeground(true);
    }

    /**
     * Creates a notification for placing the service into the foreground
     *
     * @return a notification for interacting with the service when in the foreground
     */
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle("Service is Active")
                .setContentText("Tap to return to the Map")
                .setSmallIcon(R.mipmap.ic_launcher);

        Intent resultIntent = new Intent(this, MapsActivity.class);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(this, 0, resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        return builder.build();
    }

    private void sendNotification(String title, String content) {
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content);

        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(this, MapsActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(contentIntent);
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.defaults |= Notification.DEFAULT_SOUND;
        manager.notify(new Random().nextInt(), notification);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        displayLocation();
        startLocationUpdate();
    }

    private void startLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        displayLocation();
    }

    interface LocationChangeListener {
        void onLocationChange(Location location);
    }

    public void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FATEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    public void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
        mGoogleApiClient.connect();
    }

    public void displayLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            final double latitude = mLastLocation.getLatitude();
            final double longitude = mLastLocation.getLongitude();

            geoFire.setLocation("You", new GeoLocation(latitude, longitude), new GeoFire.CompletionListener() {
                @Override
                public void onComplete(String key, DatabaseError error) {
                    if (mLocationChangeListener!=null) {
                        mLocationChangeListener.onLocationChange(mLastLocation);
                    }
                }
            });



            Log.d("MRF", String.format("Your last location was chaged: %f / %f", latitude, longitude));
        } else {
            Log.d("MRF", "Can not get your location.");
        }
    }

    public void setLocationChangeListener(LocationChangeListener mLocationChangeListener) {
        this.mLocationChangeListener = mLocationChangeListener;
    }
}