package com.danvarsh.weatherbug.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import com.danvarsh.weatherbug.R;
import com.danvarsh.weatherbug.activities.MainActivity;
import com.danvarsh.weatherbug.models.Weather;
import com.danvarsh.weatherbug.models.WeatherViewHolder;

public class WeatherRecyclerAdapter extends RecyclerView.Adapter<WeatherViewHolder> {
    private List<Weather> itemList;
    private Context context;

    public WeatherRecyclerAdapter(Context context, List<Weather> itemList) {
        this.itemList = itemList;
        this.context = context;
    }

    @Override
    public WeatherViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_row, null);
        WeatherViewHolder viewHolder = new WeatherViewHolder(view);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(WeatherViewHolder customViewHolder, int i) {
        Weather weatherItem = itemList.get(i);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        float temperature = MainActivity.convertTemperature(Float.parseFloat(weatherItem.getTemperature()));
        double wind = Double.parseDouble(weatherItem.getWind());
        double pressure = (float) Double.parseDouble(weatherItem.getPressure());
        String defaultDateFormat = context.getResources().getStringArray(R.array.dateFormatsValues)[0];
        String dateFormat = sp.getString("dateFormat", defaultDateFormat);
        if ("custom".equals(dateFormat)) {
            dateFormat = sp.getString("dateFormatCustom", defaultDateFormat);
        }
        String dateString;
        try {
            SimpleDateFormat resultFormat = new SimpleDateFormat(dateFormat);
            dateString = resultFormat.format(weatherItem.getDate());
        } catch (IllegalArgumentException e) {
            dateString = context.getResources().getString(R.string.error_dateFormat);
        }

        customViewHolder.itemDate.setText(dateString);
        if (sp.getBoolean("displayDecimalZeroes", false)) {
            customViewHolder.itemTemperature.setText(new DecimalFormat("#.0").format(temperature) + " " + sp.getString("unit", "C"));
        } else {
            customViewHolder.itemTemperature.setText(new DecimalFormat("#.#").format(temperature) + " " + sp.getString("unit", "C"));
        }
        customViewHolder.itemDescription.setText(weatherItem.getDescription().substring(0, 1).toUpperCase() +
                weatherItem.getDescription().substring(1));
        Typeface weatherFont = Typeface.createFromAsset(context.getAssets(), "fonts/weather.ttf");
        customViewHolder.itemIcon.setTypeface(weatherFont);
        customViewHolder.itemIcon.setText(weatherItem.getIcon());
        customViewHolder.itemyWind.setText(context.getString(R.string.wind) + ": " + new DecimalFormat("#.0").format(wind) + " " + "m/s");
        customViewHolder.itemPressure.setText(context.getString(R.string.pressure) + ": " + new DecimalFormat("#.0").format(pressure) + " " + "hPa");
        customViewHolder.itemHumidity.setText(context.getString(R.string.humidity) + ": " + weatherItem.getHumidity() + " %");
    }

    @Override
    public int getItemCount() {
        return (null != itemList ? itemList.size() : 0);
    }
}
