package com.feup.sdis.mapapp;


import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * This class implements the Maze Player activity
 * It's where the user can play a maze he downloaded from the server
 */
public class MazePlayerActivity extends AppCompatActivity
        implements OnMapReadyCallback,
                GoogleApiClient.ConnectionCallbacks,
                GoogleApiClient.OnConnectionFailedListener,
                ResultCallback<LocationSettingsResult>,
                LocationListener{


    /** Tag used for logs */
    private static final String TAG = MazePlayerActivity.class.getSimpleName();

    /** Max range player can be from maze (in meters) */
    public static double TOLERANCE = 3.5;

    /** Max distance a play can move between location updates */
    public static double MOVE_TOLERANCE = 20;

    /** Location request interval, in milliseconds */
    private static final int LOCATION_REQUEST_INTERVAL = 5000;

    /** Location request fastest interval, in milliseconds */
    private static final int LOCATION_REQUEST_FASTEST_INTERVAL = 5000;

    /** GoogleApiClient */
    private GoogleApiClient googleApiClient = null;

    /** MapFragment */
    private SupportMapFragment mapFragment = null;

    /** LocationRequest */
    private LocationRequest locationRequest = null;

    /** GoogleMap */
    private GoogleMap map = null;

    /** Request code used for callback onRequestPermissionsResult */
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    /** Request code user for callback onActivityResult */
    private static final int REQUEST_CHECK_SETTINGS = 2;

    /** True if user gave permissions. False otherwise */
    private boolean locationPermissionGranted = false;

    /** Last known location */
    private Marker lastKnownLocation = null;

    /** Last location that was within the maze */
    private Marker lastValidLocation = null;

    /** List of all polylines that make up this maze */
    private ArrayList<Polyline> maze = new ArrayList<>();

    /** Entrance to the maze */
    private Marker entrance = null;

    /** Exit to the maze */
    private Marker exit = null;

    /** Default map zoom */
    private static final int MIN_ZOOM = 18;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maze_player);

        // Build the Play services client for use by the Fused Location Provider
        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,
                        this /* OnConnectionFailedListener */)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();

        // Create location request
        locationRequest = LocationRequest.create()
                .setInterval(LOCATION_REQUEST_INTERVAL)
                .setFastestInterval(LOCATION_REQUEST_FASTEST_INTERVAL)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TODO check if this is before or after super call
        if (googleApiClient.isConnected())
            googleApiClient.disconnect();
    }


    /**
     * Builds the map when the Google Play services client is successfully connected.
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {

        // Build location settings request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true);

        // Check whether current location settings are satisfied
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback(this);
    }


    /**
     * Checks whether current location settings are satisfied
     */
    @Override
    public void onResult(@NonNull LocationSettingsResult result) {

        final Status status = result.getStatus();

        switch (status.getStatusCode()) {

            case LocationSettingsStatusCodes.SUCCESS:
                // All location settings are satisfied. The client can initialize location
                // requests here.

                // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map_maze_player);
                mapFragment.getMapAsync(this);

                break;

            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                // Location settings are not satisfied. But could be fixed by showing the user
                // a dialog.

                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    status.startResolutionForResult(this, REQUEST_CHECK_SETTINGS);

                } catch (IntentSender.SendIntentException e) {
                    // Log error
                    Log.d(TAG, "Location settings request failed: Exception at startResolutionForResult: "
                            + e.getMessage());
                }
                break;

            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                // Location settings are not satisfied. However, we have no way to fix the
                // settings so we won't show the dialog.
                Toast toast = Toast.makeText(this, "Can't use location services. Impossible to play a maze", Toast.LENGTH_LONG);
                toast.show();
                finish();

                break;

            default:
                break;
        }
    }


    /**
     * Handles the result of the request for location settings
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

            case REQUEST_CHECK_SETTINGS:

                switch (resultCode) {

                    case Activity.RESULT_OK:
                        // All required changes were successfully made

                        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                                .findFragmentById(R.id.map_maze_player);
                        mapFragment.getMapAsync(this);

                        break;

                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        Toast toast = Toast.makeText(this, "Can't use location services. Impossible to play a maze", Toast.LENGTH_LONG);
                        toast.show();
                        finish();

                        break;

                    default:
                        break;
                }

                break;

            default:
                break;
        }
    }


    /**
     * Handles failure to connect to the Google Play services client
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Refer to the reference doc for ConnectionResult to see what error codes might
        // be returned in onConnectionFailed.
        Log.d(TAG, "Play services connection failed: ConnectionResult.getErrorCode() = "
                + connectionResult.getErrorCode());
    }


    /**
     * Handles suspension of the connection to the Google Play services client
     */
    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Play services connection suspended");
    }


    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        map = googleMap;

        UiSettings uiSettings = map.getUiSettings();

        // disable unwanted ui settings
        uiSettings.setMapToolbarEnabled(false);
        uiSettings.setIndoorLevelPickerEnabled(false);
        uiSettings.setScrollGesturesEnabled(false);
        uiSettings.setTiltGesturesEnabled(false);

        map.setMinZoomPreference(MIN_ZOOM);

        // Get the current location of the device and set the position of the map.
        startLocationUpdates();



        // TODO download maze from server and remove hardcoded stuff
        Polyline polyline1 = map.addPolyline(new PolylineOptions()
                .addAll(PolyUtil.decode("itizFlins@B???????@?????@@??????@?????????@??????@??@???????@??????@@???????????@??@????@???????@??@????@?????@@????@???????@??????@??@?????????@??@??????@?????????@?A@@???????@????@??@???????@?????@???@@????@?????@????@??@?????????@?????????@@????@?????@@????@???????@??@??????@???"))
                .color(Color.BLUE));

        Polyline polyline2 = map.addPolyline(new PolylineOptions()
                .addAll(PolyUtil.decode("}sizFpins@AC???A???A???????A???????A?????A???????A???A???A?????A???????A?????A@A???A?????A?????A?A???A???A????@??A???????A??????@?????????@??A?????A?????A??"))
                .color(Color.BLUE));

        Polyline polyline3 = map.addPolyline(new PolylineOptions()
                .addAll(PolyUtil.decode("orizF|ins@BE?A???A?A???A?A@A?A@A?ABC?A@EBC@C@C@E@C@E@E?C@C?A?A@??A???A@??A?C?A@A?C?A?A???A@????A@M?C?C?C?A?A@??A???A??@??A???A?A"))
                .color(Color.BLUE));

        Polyline polyline4 = map.addPolyline(new PolylineOptions()
                .addAll(PolyUtil.decode("aqizFhens@AG?A?C?A?E?C?E@E?E?E@C?C@E?C?A??@A???A???A??@A???A???A????@A???A?A@A???A?A???A?A?A@A???A?A???A?A?A??@A?A???A??@A?A?A???A?A??@A?A???A???A???A???A???A???A???A???A?A???A?A?A?????A???A???A?A???A@????A???A"))
                .color(Color.BLUE));

        Polyline polyline5 = map.addPolyline(new PolylineOptions()
                .addAll(PolyUtil.decode("kpizF~_ns@?G?A???A???A?A?A?A???C??@A???A?A?A???A??@A?A???A???A???A?A???A?A???A@A???A???C?A???A@I?A?A@A?A?A?A?A?A???A?A@A?A???A???A???A@A???A?A???A?A?A@A?A?A???C???A@??A???A?A???A@C?A?C?C?C@E?A?A???A@??A?????A@??A???A?A@??A???A@A?A"))
                .color(Color.BLUE));

        Polyline polyline6 = map.addPolyline(new PolylineOptions()
                .addAll(PolyUtil.decode("irizFdjns@AE?A???A???A???A?A???A???A???A"))
                .color(Color.BLUE));

        maze.add(polyline1);
        maze.add(polyline2);
        maze.add(polyline3);
        maze.add(polyline4);
        maze.add(polyline5);
        maze.add(polyline6);

        entrance = map.addMarker(new MarkerOptions()
                .position(new LatLng(41.17825551202372, -8.598213940858841))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // same as entrance
        lastValidLocation = map.addMarker(new MarkerOptions()
                .position(entrance.getPosition())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
        lastValidLocation.setVisible(false);

        exit = map.addMarker(new MarkerOptions()
                .position(new LatLng(41.17767384142088, -8.595726862549782))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        map.moveCamera(CameraUpdateFactory.newLatLng(entrance.getPosition()));
    }


    /**
     * Calls for regular location updates
     */
    public void startLocationUpdates() {

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (locationPermissionGranted) {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        }
    }


    /**
     * Handles location update
     */
    @Override
    public void onLocationChanged(Location location) {

        LatLng latLng = new LatLng(
                location.getLatitude(),
                location.getLongitude());

        Log.d("dani", latLng.toString());
        Log.d("dani", DateFormat.getTimeInstance().format(new Date()));

        update(latLng);
    }


    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionGranted = true;
                }
            }
        }
    }


    /**
     * Updates the map (ie move camera)
     */
    private void update(LatLng lastKnownLatLng) {

        // remove previous marker
        if (lastKnownLocation != null) {
            lastKnownLocation.remove();
        }

        // set marker at new position
        lastKnownLocation = map.addMarker(new MarkerOptions()
                .position(lastKnownLatLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        // Set the map's camera position to the current location of the device
        map.moveCamera(CameraUpdateFactory.newLatLng(
               lastKnownLatLng));


        if (isPlayerOnMaze()) {

            if (SphericalUtil.computeDistanceBetween(lastKnownLatLng, lastValidLocation.getPosition()) > MOVE_TOLERANCE) {
                Toast.makeText(this, "Please return to your previous location", Toast.LENGTH_SHORT).show();
                lastValidLocation.setVisible(true);
            } else {
                lastValidLocation.setVisible(false);
            }
        }
        else {
            Toast.makeText(this, "Please return to your previous location", Toast.LENGTH_SHORT).show();
            lastValidLocation.setVisible(true);
        }
    }

    private boolean isPlayerOnMaze() {

        if (SphericalUtil.computeDistanceBetween(lastKnownLocation.getPosition(), entrance.getPosition()) < TOLERANCE ||
                SphericalUtil.computeDistanceBetween(lastKnownLocation.getPosition(), exit.getPosition()) < TOLERANCE) {
            return true;
        }

        for (Polyline polyline : maze) {

            if (PolyUtil.isLocationOnPath(lastKnownLocation.getPosition(), polyline.getPoints(), polyline.isGeodesic(), TOLERANCE)) {
                return true;
            }
        }

        return false;
    }
}
