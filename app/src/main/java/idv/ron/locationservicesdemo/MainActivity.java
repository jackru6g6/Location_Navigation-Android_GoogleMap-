package idv.ron.locationservicesdemo;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_CODE_RESOLUTION = 1;
    private final static String TAG = "MainActivity";
    private GoogleApiClient googleApiClient;
    private Location lastLocation;

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {//傳經緯度
            updateLastLocationInfo(location);//刷新畫面
            lastLocation = location;
        }
    };

    private GoogleApiClient.ConnectionCallbacks connectionCallbacks =
            new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle bundle) {//建立連結成功
                    Log.i(TAG, "GoogleApiClient connected");
                    if (ActivityCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED) {//如果同意
                        lastLocation = LocationServices.FusedLocationApi
                                .getLastLocation(googleApiClient);//.getLastLocation(googleApiClient)，請求最後一次定位的位置，歷史資料
                        //------------------------抓最新的位置--------------------------------
                        LocationRequest locationRequest = LocationRequest.create()
                                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)//PRIORITY_HIGH_ACCURACY最高精準度的定位
                                .setInterval(10000)//10000ms，每幾秒查詢一次位置(頻率)
                                .setSmallestDisplacement(1000);//1000m，距離上次位置距離差多少才會呼叫locationListener
                        LocationServices.FusedLocationApi.requestLocationUpdates(//更新位置
                                googleApiClient, locationRequest, locationListener);//(GoogleApiClient,LocationRequest程式,更新位置(如果有會自動呼叫onLocationChanged()))
                        //-------------------------------------------------------------------
                        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient,locationListener);//解除註冊，取消定位
                    }
                }

                @Override
                public void onConnectionSuspended(int i) {
                    showToast(R.string.msg_GoogleApiClientConnectionSuspended);
                }
            };

    private GoogleApiClient.OnConnectionFailedListener onConnectionFailedListener =
            new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(@NonNull ConnectionResult result) {
                    showToast(R.string.msg_GoogleApiClientConnectionFailed);
                    if (!result.hasResolution()) {
                        GoogleApiAvailability.getInstance().getErrorDialog(
                                MainActivity.this,
                                result.getErrorCode(),
                                0
                        ).show();
                        return;
                    }
                    try {
                        result.startResolutionForResult(
                                MainActivity.this,
                                REQUEST_CODE_RESOLUTION);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(TAG, "Exception while starting resolution activity");
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(connectionCallbacks)//回應(監聽器)
                    .addOnConnectionFailedListener(onConnectionFailedListener)
                    .build();
        }
        googleApiClient.connect();
    }

    @Override
    protected void onPause() {
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_RESOLUTION) {
                googleApiClient.connect();
            }
        }
    }

    private void updateLastLocationInfo(Location lastLocation) {
        TextView tvLastLocation = (TextView) findViewById(R.id.tvLastLocation);
        String message = "";
        message += "The Information of the Last Location \n";

        if (lastLocation == null) {
            showToast(R.string.msg_LastLocationNotAvailable);
            return;
        }

        Date date = new Date(lastLocation.getTime());
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        String time = dateFormat.format(date);
        message += "fix time: " + time + "\n";

        message += "latitude: " + lastLocation.getLatitude() + "\n";
        message += "longitude: " + lastLocation.getLongitude() + "\n";
        message += "accuracy (meters): " + lastLocation.getAccuracy() + "\n";
        message += "altitude (meters): " + lastLocation.getAltitude() + "\n";
        message += "bearing (horizontal direction- in degrees): "
                + lastLocation.getBearing() + "\n";
        message += "speed (meters/second): " + lastLocation.getSpeed() + "\n";

        tvLastLocation.setText(message);
    }

    public void onDistanceClick(View view) {
        EditText etLocationName = (EditText) findViewById(R.id.etLocationName);
        TextView tvDistance = (TextView) findViewById(R.id.tvDistance);
        String locationName = etLocationName.getText().toString().trim();

        if (!lastLocationFound() || !inputValid(locationName)) {
            return;
        }

        Address address = getAddress(locationName);//使用者所輸入的地點轉成經緯度
        if (address == null) {
            showToast(R.string.msg_LocationNotAvailable);
            return;
        }

        float[] results = new float[1];//存在results[0]
        Location.distanceBetween(lastLocation.getLatitude(),
                lastLocation.getLongitude(), address.getLatitude(),
                address.getLongitude(), results);//計算兩點間的距離(起點緯,經,終點緯,經,將回傳值存的地方)
        String text = String.format(Locale.getDefault(),
                "the distance between the last location and %s is %.2f meter(s).",
                locationName,
                results[0]
        );
        tvDistance.setText(text);
    }

    public void onDirectClick(View view) {
        EditText etLocationName = (EditText) findViewById(R.id.etLocationName);
        String locationName = etLocationName.getText().toString().trim();

        if (!lastLocationFound() || !inputValid(locationName)) {
            return;
        }

        Address address = getAddress(locationName);
        if (address == null) {
            showToast(R.string.msg_LocationNotAvailable);
            return;
        }

        double fromLat = lastLocation.getLatitude();
        double fromLng = lastLocation.getLongitude();
        double toLat = address.getLatitude();
        double toLng = address.getLongitude();

        direct(fromLat, fromLng, toLat, toLng);
    }

    private Address getAddress(String locationName) {
        Geocoder geocoder = new Geocoder(this);
        List<Address> addressList = null;

        try {
            addressList = geocoder.getFromLocationName(locationName, 1);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        if (addressList == null || addressList.isEmpty()) {
            return null;
        } else {
            return addressList.get(0);
        }
    }

    private void direct(double fromLat, double fromLng, double toLat,
                        double toLng) {
        String uriStr = String.format(Locale.US,
                "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f", fromLat,
                fromLng, toLat, toLng);
        Intent intent = new Intent();
        intent.setClassName("com.google.android.apps.maps",
                "com.google.android.maps.MapsActivity");
        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uriStr));
        startActivity(intent);
    }

    private boolean lastLocationFound() {
        if (lastLocation == null) {
            showToast(R.string.msg_LocationNotAvailable);
            return false;
        }
        return true;
    }

    private boolean inputValid(String input) {
        if (input == null || input.length() <= 0) {
            showToast(R.string.msg_InvalidInput);
            return false;
        }
        return true;
    }

    private void showToast(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        askPermissions();
    }

    private static final int REQ_PERMISSIONS = 0;

    // New Permission see Appendix A
    private void askPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        Set<String> permissionsRequest = new HashSet<>();
        for (String permission : permissions) {
            int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                permissionsRequest.add(permission);
            }
        }

        if (!permissionsRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsRequest.toArray(new String[permissionsRequest.size()]),
                    REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQ_PERMISSIONS:
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        String text = getString(R.string.text_ShouldGrant);
                        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                break;
        }
    }
}