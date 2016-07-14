package com.automotive.hhi.mileagetracker.view.interfaces;

import android.content.Intent;

import com.automotive.hhi.mileagetracker.adapters.FillupAdapter;
import com.automotive.hhi.mileagetracker.model.data.Car;

/**
 * Created by Josiah Hadley on 3/24/2016.
 */
public interface CarDetailView extends MvpView {

    void showFillups(FillupAdapter fillupAdapter);

    void showCar(Car car);

    void launchActivity(Intent intent, int code);

    void close();
    
}
