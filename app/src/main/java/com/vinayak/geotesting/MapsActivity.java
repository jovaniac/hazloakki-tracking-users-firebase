package com.vinayak.geotesting;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.h6ah4i.android.widget.verticalseekbar.VerticalSeekBar;
import com.mahmud.geotesting.R;


import java.util.Random;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {


    private GoogleMap mMap;
    //Play Service Location
    private static final int MY_PERMISSION_REQUEST_CODE = 7192;
    private static final int PLAY_SERVICE_RESULATION_REQUEST = 300193;

    private Location mLastLocaiton;

    private static int UPDATE_INTERVAL = 1000;
    private static int FATEST_INTERVAL = 1000;
    private static int DISPLACEMENT = 1;


    Marker mCurrent;
    VerticalSeekBar mVerticalSeekBar;
    private static final String TAG = MapsActivity.class.getSimpleName();

    private GeoService geoService;
    private boolean serviceBound;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.



        mVerticalSeekBar = (VerticalSeekBar) findViewById(R.id.verticalSeekBar);
        mVerticalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mMap.animateCamera(CameraUpdateFactory.zoomTo(progress), 1500, null);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

       // loginToFirebase();


    }


    private void setUpdateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, MY_PERMISSION_REQUEST_CODE);
        } else {
            if (checkPlayService()) {
                geoService.buildGoogleApiClient();
                geoService.createLocationRequest();
                geoService.displayLocation();

                geoService.setLocationChangeListener(new GeoService.LocationChangeListener() {
                    @Override
                    public void onLocationChange(Location location) {

                        if (mCurrent != null)
                            mCurrent.remove();

                        mCurrent = mMap.addMarker(new MarkerOptions()
                                .position(new LatLng(location.getLatitude(), location.getLongitude()))
                                .title("You"+location.getLatitude()));

                        LatLng coordinate = new LatLng(location.getLatitude(), location.getLongitude());

                        CameraUpdate yourLocation = CameraUpdateFactory.newLatLngZoom(coordinate, 20);
                        mMap.animateCamera(yourLocation);
                    }
                });
            }
        }
    }


    private boolean checkPlayService() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            if (googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result, PLAY_SERVICE_RESULATION_REQUEST).show();
            } else {
                Toast.makeText(this, "This Device is not supported.", Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        return true;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng dangerous_area = new LatLng(19.315315, -99.134100);
        mMap.addCircle(new CircleOptions()
                .center(dangerous_area)
                .radius(2000)
                .strokeColor(Color.BLUE)
                .fillColor(0x220000FF)
                .strokeWidth(5.0f));

        geoService.startService(dangerous_area,2000);


    }


    @Override
    protected void onStart() {
        super.onStart();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Starting and binding service");
        }
        Toast.makeText(this, "Starting and binding service", Toast.LENGTH_SHORT).show();

        Intent i = new Intent(this, GeoService.class);

        startService(i);

        bindService(i, mConnection, 0);

    }

    @Override
    protected void onStop() {
        super.onStop();

        Toast.makeText(this, "onStop", Toast.LENGTH_SHORT).show();

        if (serviceBound) {
            // If a timer is active, foreground the service, otherwise kill the service
            if (geoService.isServiceRunning()) {
                geoService.foreground();
            } else {
                stopService(new Intent(this, GeoService.class));
            }
            // Unbind the service
            unbindService(mConnection);
            serviceBound = false;
        }
    }

    /**
     * Callback for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Service bound");
            }

            Toast.makeText(getApplicationContext(), "Service bound", Toast.LENGTH_SHORT).show();

            GeoService.RunServiceBinder binder = (GeoService.RunServiceBinder) service;
            geoService = binder.getService();
            serviceBound = true;
            // Ensure the service is not in the foreground when bound
            geoService.background();

            setUpdateLocation();

            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(MapsActivity.this);

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Service disconnect");
            }
            Toast.makeText(getApplicationContext(), "Service disconnect", Toast.LENGTH_SHORT).show();
            serviceBound = false;
        }
    };


    public static class GeoService extends Service implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            LocationListener {

        private LocationRequest mLocationRequest;
        private GoogleApiClient mGoogleApiClient;
        private Location mLastLocation;
        private DatabaseReference ref;
        private GeoFire geoFire;
        private LocationChangeListener mLocationChangeListener;
        private static final String TAG = GeoService.class.getSimpleName();



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


            String email = "jovaniac@test.com";
            String password = "123456";

            FirebaseAuth.getInstance().signInWithEmailAndPassword(
                    email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(Task<AuthResult> task) {

                    if (task.isSuccessful()) {
                        Toast.makeText(getApplicationContext(), "Conexion exitosa", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Firebase authentication failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            ref = FirebaseDatabase.getInstance().getReference("users-tracking");
            //ref2 = FirebaseFirestore.getInstance().collection("users-tracking");

            geoFire = new GeoFire(ref);
            //geoFire2= new com.koalap.geofirestore.GeoFire(ref2);
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
            Toast.makeText(this, "Binding service", Toast.LENGTH_SHORT).show();

            return serviceBinder;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Destroying service");
            }
            Toast.makeText(this, "Destroying service", Toast.LENGTH_SHORT).show();
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

        private void createLocationRequest() {
            mLocationRequest = new LocationRequest();
            mLocationRequest.setInterval(UPDATE_INTERVAL);
            mLocationRequest.setFastestInterval(FATEST_INTERVAL);
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
        }

        private void buildGoogleApiClient() {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API).build();
            mGoogleApiClient.connect();
        }

        private void displayLocation() {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (mLastLocation != null) {
                final double latitude = mLastLocation.getLatitude();
                final double longitude = mLastLocation.getLongitude();

                Toast.makeText(getApplicationContext(), "ubicacion trackin :) Latitude "+latitude + " Longitud: "+longitude, Toast.LENGTH_SHORT).show();

              geoFire.setLocation("Isra", new GeoLocation(latitude, longitude), new GeoFire.CompletionListener() {

                    @Override
                    public void onComplete(String key, DatabaseError error) {
                        if (mLocationChangeListener!=null) {
                            mLocationChangeListener.onLocationChange(mLastLocation);
                        }
                    }
                });


                //Log.d("MRF", String.format("Your last location was chaged: %f / %f", latitude, longitude));
                //Toast.makeText(getApplicationContext(), "Your last location was chaged:", Toast.LENGTH_SHORT).show();
            } else {
                Log.d("MRF", "Can not get your location.");
                Toast.makeText(getApplicationContext(), "Can not get your location.", Toast.LENGTH_SHORT).show();

            }
        }

        public void setLocationChangeListener(LocationChangeListener mLocationChangeListener) {
            this.mLocationChangeListener = mLocationChangeListener;
        }

    }


}
