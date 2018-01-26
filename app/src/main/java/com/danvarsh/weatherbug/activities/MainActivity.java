package com.danvarsh.weatherbug.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.danvarsh.weatherbug.AlarmReceiver;
import com.danvarsh.weatherbug.Constants;
import com.danvarsh.weatherbug.R;
import com.danvarsh.weatherbug.adapters.ViewPagerAdapter;
import com.danvarsh.weatherbug.adapters.WeatherRecyclerAdapter;
import com.danvarsh.weatherbug.fragments.RecyclerViewFragment;
import com.danvarsh.weatherbug.models.Weather;
import com.danvarsh.weatherbug.tasks.GenericRequestTask;
import com.danvarsh.weatherbug.tasks.ParseResult;

public class MainActivity extends AppCompatActivity {
    Typeface weatherFont;
    Weather todayWeather = new Weather();

    TextView todayTemperature;
    TextView todayDescription;
    TextView todayWind;
    TextView todayPressure;
    TextView todayHumidity;
    TextView todaySunrise;
    TextView todaySunset;
    TextView lastUpdate;
    TextView todayIcon;
    ViewPager viewPager;
    TabLayout tabLayout;

    View appView;
    ProgressDialog progressDialog;
    boolean destroyed = false;

    private List<Weather> longTermTodayWeather = new ArrayList<>();
    private List<Weather> longTermTomorrowWeather = new ArrayList<>();

    public String recentCity = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appView = findViewById(R.id.viewApp);
        progressDialog = new ProgressDialog(MainActivity.this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize textboxes
        todayTemperature = (TextView) findViewById(R.id.todayTemperature);
        todayDescription = (TextView) findViewById(R.id.todayDescription);
        todayWind = (TextView) findViewById(R.id.todayWind);
        todayPressure = (TextView) findViewById(R.id.todayPressure);
        todayHumidity = (TextView) findViewById(R.id.todayHumidity);
        todaySunrise = (TextView) findViewById(R.id.todaySunrise);
        todaySunset = (TextView) findViewById(R.id.todaySunset);
        lastUpdate = (TextView) findViewById(R.id.lastUpdate);
        todayIcon = (TextView) findViewById(R.id.todayIcon);
        weatherFont = Typeface.createFromAsset(this.getAssets(), "fonts/weather.ttf");
        todayIcon.setTypeface(weatherFont);

        // Initialize viewPager
        viewPager = (ViewPager) findViewById(R.id.viewPager);
        tabLayout = (TabLayout) findViewById(R.id.tabs);

        destroyed = false;

        // Preload data from cache
        preloadWeather();
        updateLastUpdateTime();

        // Set autoupdater
        AlarmReceiver.setRecurringAlarm(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // This method is for handling configuration changes if occurs
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        } else if (newConfig.densityDpi == Configuration.DENSITY_DPI_UNDEFINED) {
            Toast.makeText(this, "Width Undefined", Toast.LENGTH_SHORT).show();
        } else if (newConfig.touchscreen == Configuration.TOUCHSCREEN_FINGER) {
            Toast.makeText(this, "Finger Touchscreen", Toast.LENGTH_SHORT).show();
        }
    }

    public WeatherRecyclerAdapter getAdapter(int id) {
        WeatherRecyclerAdapter weatherRecyclerAdapter;
        if (id == 0) {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermTodayWeather);
        } else {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermTomorrowWeather);
        }
        return weatherRecyclerAdapter;
    }

    private void preloadWeather() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String lastToday = sp.getString("lastToday", "");
        if (!lastToday.isEmpty()) {
            new TodayWeatherTask(this, this, progressDialog).execute("cachedResponse", lastToday);
        }
        String lastLongterm = sp.getString("lastLongterm", "");
        if (!lastLongterm.isEmpty()) {
            new LongTermWeatherTask(this, this, progressDialog).execute("cachedResponse", lastLongterm);
        }
    }

    private void getTodayWeather() {
        new TodayWeatherTask(this, this, progressDialog).execute();
    }

    private void getLongTermWeather() { new LongTermWeatherTask(this, this, progressDialog).execute(); }

    private void searchCities() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(this.getString(R.string.search_title));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setMaxLines(1);
        input.setSingleLine(true);
        alert.setView(input, 32, 0, 32, 0);
        alert.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String result = input.getText().toString();
                if (!result.isEmpty()) {
                    saveLocation(result);
                }
            }
        });
        alert.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Cancelled
            }
        });
        alert.show();
    }

    private void shareData() {
        Intent i = new Intent(android.content.Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(android.content.Intent.EXTRA_TEXT, "@layout/activity_main");
        startActivity(Intent.createChooser(i, "Share"));
    }

    private void saveLocation(String result) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        recentCity = preferences.getString("city", Constants.DEFAULT_CITY);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("city", result);
        editor.commit();
        if (!recentCity.equals(result)) {
            getTodayWeather();
            getLongTermWeather();
        }
    }

    private String setWeatherIcon(int actualId, int hourOfDay) {
        int id = actualId / 100;
        String icon = "";
        if (actualId == 800) {
            if (hourOfDay >= 7 && hourOfDay < 20) {
                icon = this.getString(R.string.weather_sunny);
            } else {
                icon = this.getString(R.string.weather_clear_night);
            }
        } else {
            switch (id) {
                case 2:
                    icon = this.getString(R.string.weather_thunder);
                    break;
                case 3:
                    icon = this.getString(R.string.weather_drizzle);
                    break;
                case 7:
                    icon = this.getString(R.string.weather_foggy);
                    break;
                case 8:
                    icon = this.getString(R.string.weather_cloudy);
                    break;
                case 6:
                    icon = this.getString(R.string.weather_snowy);
                    break;
                case 5:
                    icon = this.getString(R.string.weather_rainy);
                    break;
            }
        }
        return icon;
    }

    private ParseResult parseTodayJson(String result) {
        try {
            JSONObject reader = new JSONObject(result);
            final String code = reader.optString("cod");
            if ("404".equals(code)) {
                return ParseResult.CITY_NOT_FOUND;
            }

            String city = reader.getString("name");
            String country = "";
            JSONObject countryObj = reader.optJSONObject("sys");
            if (countryObj != null) {
                country = countryObj.getString("country");
                todayWeather.setSunrise(countryObj.getString("sunrise"));
                todayWeather.setSunset(countryObj.getString("sunset"));
            }
            todayWeather.setCity(city);
            todayWeather.setCountry(country);

            JSONObject coordinates = reader.getJSONObject("coord");
            if (coordinates != null) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
                sp.edit().putFloat("latitude", (float) coordinates.getDouble("lon")).putFloat("longitude", (float) coordinates.getDouble("lat")).commit();
            }

            JSONObject main = reader.getJSONObject("main");
            todayWeather.setTemperature(main.getString("temp"));
            todayWeather.setDescription(reader.getJSONArray("weather").getJSONObject(0).getString("description"));
            JSONObject windObj = reader.getJSONObject("wind");
            todayWeather.setWind(windObj.getString("speed"));
            todayWeather.setPressure(main.getString("pressure"));
            todayWeather.setHumidity(main.getString("humidity"));

            final String idString = reader.getJSONArray("weather").getJSONObject(0).getString("id");
            todayWeather.setId(idString);
            todayWeather.setIcon(setWeatherIcon(Integer.parseInt(idString), Calendar.getInstance().get(Calendar.HOUR_OF_DAY)));

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("lastToday", result);
            editor.commit();
        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return ParseResult.JSON_EXCEPTION;
        }
        return ParseResult.OK;
    }

    private void updateTodayWeatherUI() {
        try {
            if (todayWeather.getCountry().isEmpty()) {
                preloadWeather();
                return;
            }
        } catch (Exception e) {
            preloadWeather();
            return;
        }
        String city = todayWeather.getCity();
        String country = todayWeather.getCountry();
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
        getSupportActionBar().setTitle(city + (country.isEmpty() ? "" : ", " + country));

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        float temperature = convertTemperature(Float.parseFloat(todayWeather.getTemperature()));
        double wind = Double.parseDouble(todayWeather.getWind());
        double pressure = (float) Double.parseDouble(todayWeather.getPressure());

        todayTemperature.setText(new DecimalFormat("#.#").format(temperature) + " " + sp.getString("unit", "C"));
        todayDescription.setText(todayWeather.getDescription().substring(0, 1).toUpperCase()
                + todayWeather.getDescription().substring(1));
        todayWind.setText(getString(R.string.wind) + ": " + new DecimalFormat("#.0").format(wind) + " " + "m/s");
        todayPressure.setText(getString(R.string.pressure) + ": " + new DecimalFormat("#.0").format(pressure) + " " + "hPa");
        todayHumidity.setText(getString(R.string.humidity) + ": " + todayWeather.getHumidity() + " %");
        todaySunrise.setText(getString(R.string.sunrise) + ": " + timeFormat.format(todayWeather.getSunrise()));
        todaySunset.setText(getString(R.string.sunset) + ": " + timeFormat.format(todayWeather.getSunset()));
        todayIcon.setText(todayWeather.getIcon());
    }

    public static float convertTemperature(float Temp) {
        return Temp - 273.15f;
    }

    public ParseResult parseLongTermJson(String result) {
        int i;
        try {
            JSONObject reader = new JSONObject(result);

            final String code = reader.optString("cod");
            if ("404".equals(code)) {
                if (longTermTomorrowWeather == null) {
                    longTermTodayWeather = new ArrayList<>();
                    longTermTomorrowWeather = new ArrayList<>();
                }
                return ParseResult.CITY_NOT_FOUND;
            }

            longTermTodayWeather = new ArrayList<>();
            longTermTomorrowWeather = new ArrayList<>();

            JSONArray list = reader.getJSONArray("list");
            for (i = 0; i < list.length(); i++) {
                Weather weather = new Weather();

                JSONObject listItem = list.getJSONObject(i);
                JSONObject main = listItem.getJSONObject("main");

                weather.setDate(listItem.getString("dt"));
                weather.setTemperature(main.getString("temp"));
                weather.setDescription(listItem.optJSONArray("weather").getJSONObject(0).getString("description"));
                JSONObject windObj = listItem.optJSONObject("wind");
                weather.setWind(windObj.getString("speed"));
                weather.setPressure(main.getString("pressure"));
                weather.setHumidity(main.getString("humidity"));

                final String idString = listItem.optJSONArray("weather").getJSONObject(0).getString("id");
                weather.setId(idString);

                final String dateMsString = listItem.getString("dt") + "000";
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Long.parseLong(dateMsString));
                weather.setIcon(setWeatherIcon(Integer.parseInt(idString), cal.get(Calendar.HOUR_OF_DAY)));

                Calendar today = Calendar.getInstance();
                if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                    longTermTodayWeather.add(weather);
                } else if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 1) {
                    longTermTomorrowWeather.add(weather);
                }
            }
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("lastLongterm", result);
            editor.commit();
        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return ParseResult.JSON_EXCEPTION;
        }
        return ParseResult.OK;
    }

    private void updateLongTermWeatherUI() {
        if (destroyed) {
            return;
        }
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        Bundle bundleToday = new Bundle();
        bundleToday.putInt("day", 0);
        RecyclerViewFragment recyclerViewFragmentToday = new RecyclerViewFragment();
        recyclerViewFragmentToday.setArguments(bundleToday);
        viewPagerAdapter.addFragment(recyclerViewFragmentToday, getString(R.string.today));

        Bundle bundleTomorrow = new Bundle();
        bundleTomorrow.putInt("day", 1);
        RecyclerViewFragment recyclerViewFragmentTomorrow = new RecyclerViewFragment();
        recyclerViewFragmentTomorrow.setArguments(bundleTomorrow);
        viewPagerAdapter.addFragment(recyclerViewFragmentTomorrow, getString(R.string.tomorrow));

        int currentPage = viewPager.getCurrentItem();

        viewPagerAdapter.notifyDataSetChanged();
        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

        if (currentPage == 0 && longTermTodayWeather.isEmpty()) {
            currentPage = 1;
        }
        viewPager.setCurrentItem(currentPage, false);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            if (isNetworkAvailable()) {
                getTodayWeather();
                getLongTermWeather();
            } else {
                Snackbar.make(appView, getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show();
            }
            return true;
        }
        if (id == R.id.action_search) {
            searchCities();
            return true;
        }
        if (id == R.id.action_share) {
            shareData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class TodayWeatherTask extends GenericRequestTask {
        public TodayWeatherTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected ParseResult parseResponse(String response) {
            return parseTodayJson(response);
        }

        @Override
        protected String getAPIName() {
            return "weather";
        }

        @Override
        protected void updateMainUI() {
            updateTodayWeatherUI();
            updateLastUpdateTime();
        }
    }

    class LongTermWeatherTask extends GenericRequestTask {
        public LongTermWeatherTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected ParseResult parseResponse(String response) {
            return parseLongTermJson(response);
        }

        @Override
        protected String getAPIName() {
            return "forecast";
        }

        @Override
        protected void updateMainUI() {
            updateLongTermWeatherUI();
        }
    }

    public static long saveLastUpdateTime(SharedPreferences sp) {
        Calendar now = Calendar.getInstance();
        sp.edit().putLong("lastUpdate", now.getTimeInMillis()).apply();
        return now.getTimeInMillis();
    }

    private void updateLastUpdateTime() {
        updateLastUpdateTime(
                PreferenceManager.getDefaultSharedPreferences(this).getLong("lastUpdate", -1)
        );
    }

    private void updateLastUpdateTime(long timeInMillis) {
        if (timeInMillis < 0) {
            lastUpdate.setText("");
        } else {
            lastUpdate.setText(getString(R.string.last_update, formatTimeWithDayIfNotToday(this, timeInMillis)));
        }
    }

    public static String formatTimeWithDayIfNotToday(Context context, long timeInMillis) {
        Calendar now = Calendar.getInstance();
        Calendar lastCheckedCal = new GregorianCalendar();
        lastCheckedCal.setTimeInMillis(timeInMillis);
        Date lastCheckedDate = new Date(timeInMillis);
        String timeFormat = android.text.format.DateFormat.getTimeFormat(context).format(lastCheckedDate);
        if (now.get(Calendar.YEAR) == lastCheckedCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == lastCheckedCal.get(Calendar.DAY_OF_YEAR)) {
            return timeFormat;
        } else {
            return android.text.format.DateFormat.getDateFormat(context).format(lastCheckedDate) + " " + timeFormat;
        }
    }
}
