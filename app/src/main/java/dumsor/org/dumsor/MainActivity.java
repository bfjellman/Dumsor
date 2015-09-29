package dumsor.org.dumsor;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.Timer;
import java.util.TimerTask;


//TODO Implement Parse.

public class MainActivity extends Activity {

    private static final int MAX_CONNECT_TRIES = 4;
    private static final int WAIT_TIME = 2000;
    private SharedPreferences sharedPreferences;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextView lat;
    private TextView lng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = this.getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);

        ImageView imgView = (ImageView) findViewById(R.id.imageView);
        imgView.setImageResource(R.drawable.dumsor_launcher);


        lat = (TextView) findViewById(R.id.text_lat);
        lng = (TextView) findViewById(R.id.text_lng);

        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

        //listen for location changes
        locationListener = new LocationListener() {

            @Override
            public void onLocationChanged(Location location) {
                Toast.makeText(MainActivity.this, "Loc Change", Toast.LENGTH_SHORT).show();
                sharedPreferences
                        .edit()
                        .putString("lat", Double.toString(location.getLatitude()))
                        .putString("lng", Double.toString((location.getLongitude())))
                        .apply();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };
        //establish connections via GPS or netwrok
        checkConnections();

        final ToggleButton tb = (ToggleButton) findViewById(R.id.powerToggleButton);
        tb.setChecked(true);
        tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                RelativeLayout rl = (RelativeLayout) findViewById(R.id.layout_screen);
                tb.setEnabled(false);


                Timer buttonTimer = new Timer();
                buttonTimer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                tb.setEnabled(true);
                            }
                        });
                    }
                }, 5000); //was set at 60000 - 5 sec for testing

                if (isChecked) {
                    rl.setBackgroundColor(Color.WHITE);
                    Toast.makeText(getApplicationContext(), getString(R.string
                            .main_power_on_message), Toast.LENGTH_LONG).show();
                    recordLocation(1);
                } else {
                    rl.setBackgroundColor(Color.BLACK);
                    Toast.makeText(getApplicationContext(), getString(R.string
                            .main_power_off_message), Toast.LENGTH_LONG).show();
                    recordLocation(0);
                }
            }
        });
    }

    /**
     * Record the current location of the user to cloud services.
     *
     * @param powerStatus If the power is off (0) or on (1).
     */
    private void recordLocation(int powerStatus) {

        String testConnection = sharedPreferences.getString("location", null);
        //If connected via GPS update lat and lng, run a background thread to look for location update
        if("GPS".equals(testConnection) || "NETWORK".equals(testConnection)) {

            AsyncNetwork asyncNetwork = new AsyncNetwork();
            asyncNetwork.execute();

        //if not connected, recheck connections
        } else {
            Toast.makeText(MainActivity.this, "Cannot find Location - turn on GPS or connect to Network.", Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Check and start location services for the best available connection.
     *
     * @return Whether or not a connection was established.
     */
    private boolean checkConnections() {
        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(MainActivity.this, "GPS Enabled", Toast.LENGTH_SHORT).show();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            sharedPreferences
                    .edit()
                    .putString("location", "GPS")
                    .apply();
            return true;
        } else if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(MainActivity.this, "Network Enabled", Toast.LENGTH_SHORT).show();

            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
            sharedPreferences
                    .edit()
                    .putString("location", "NETWORK")
                    .apply();
            return true;

        } else {
            Toast.makeText(MainActivity.this, "No location services found", Toast.LENGTH_SHORT).show();
            sharedPreferences
                    .edit()
                    .putString("location", "NONE")
                    .apply();
            return false;
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if(!"NONE".equals(sharedPreferences.getString("location", null))) {
            locationManager.removeUpdates(locationListener);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(!"NONE".equals(sharedPreferences.getString("location", null))) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if("GPS".equals(sharedPreferences.getString("location", null))) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
        if("NETWORK".equals(sharedPreferences.getString("location", null))) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * This AsyncTask runs in the background after the button is pressed to check that status of
     * the network provider update for location.  It will look as many times as the
     * constant MAX_CONNECT_TRIES is set and wait between tries at WAIT_TIME is set for (in MS).
     *
     * If the location is found, it will update the information and if not, it will do nothing.
     */
    private class AsyncNetwork extends AsyncTask<String, String, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {

            for(int i = 0; i < MAX_CONNECT_TRIES; i++) {
                if(sharedPreferences.getString("lat", null) == null) {
                    try {
                        Thread.sleep(WAIT_TIME);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    return true;

                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {

            if(result) {
                sharedPreferences
                        .edit()
                        .putString("latold", sharedPreferences.getString("lat", null))
                        .putString("lngold", sharedPreferences.getString("lng", null))
                        .apply();

                lat.setText(sharedPreferences.getString("lat", null));
                lng.setText(sharedPreferences.getString("lng", null));

            } else {
                Toast.makeText(MainActivity.this, "No Location Updates Found.", Toast.LENGTH_SHORT).show();

            }
        }
    }
}
