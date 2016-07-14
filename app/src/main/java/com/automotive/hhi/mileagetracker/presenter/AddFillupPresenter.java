package com.automotive.hhi.mileagetracker.presenter;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.automotive.hhi.mileagetracker.KeyContract;
import com.automotive.hhi.mileagetracker.R;
import com.automotive.hhi.mileagetracker.model.data.Car;
import com.automotive.hhi.mileagetracker.model.data.CarFactory;
import com.automotive.hhi.mileagetracker.model.data.Fillup;
import com.automotive.hhi.mileagetracker.model.data.FillupFactory;
import com.automotive.hhi.mileagetracker.model.data.Station;
import com.automotive.hhi.mileagetracker.model.data.StationFactory;
import com.automotive.hhi.mileagetracker.model.database.DataContract;
import com.automotive.hhi.mileagetracker.view.interfaces.AddFillupView;
import com.automotive.hhi.mileagetracker.view.fragments.DatePickerFragment;
import com.automotive.hhi.mileagetracker.view.SelectStationActivity;

import java.util.Calendar;

/**
 * Created by Josiah Hadley on 4/1/2016.
 */
public class AddFillupPresenter implements Presenter<AddFillupView> {

    private final String LOG_TAG = AddFillupPresenter.class.getSimpleName();

    private AddFillupView mAddFillupView;
    private Context mContext;
    private Station mStation;
    private Car mCar;
    private Fillup mFillup;
    private boolean mIsEdit;

    public AddFillupPresenter(Fillup fillup
            , Car car
            , Station station
            , boolean isEdit
            , Context context){
        mContext = context;
        mIsEdit = isEdit;
        mStation = station;
        mCar = car;
        mFillup = fillup;

    }

    @Override
    public void attachView(AddFillupView view) {
        mAddFillupView = view;
        if(mIsEdit){
            mAddFillupView.setFields();
        }
    }

    @Override
    public void detachView() {
        mAddFillupView = null;
        mContext = null;
    }

    public Car getCar(){ return mCar;}

    public Fillup getFillup() { return mFillup; }

    public Station getStation(){ return mStation; }

    public void setStation(Station station){
        mStation = station;
    }

    public boolean getIsEdit(){ return mIsEdit; }

    public void launchAddStation(){
        Intent addStationIntent = new Intent(mAddFillupView.getContext(), SelectStationActivity.class);

        mAddFillupView.launchActivity(addStationIntent, KeyContract.GET_STATION_CODE);
    }

    public void checkStation(){
        if(mStation.getId()==0 && mStation.getAddress() != null){
            Cursor fillupCheckCursor = mContext
                    .getContentResolver()
                    .query(DataContract.StationTable.CONTENT_URI
                            , null
                            , DataContract.StationTable.NAME
                            + " = '" + mStation.getName()
                            + "' AND " + DataContract.StationTable.ADDRESS
                            + " = '" + mStation.getAddress() + "'", null, null);

            if(fillupCheckCursor == null || !fillupCheckCursor.moveToFirst()){
                // If the station does not exist in the db, we add it, then add
                // the returned ID to the mStation object
                mStation.setId(ContentUris.parseId(mContext.getContentResolver()
                        .insert(DataContract.StationTable.CONTENT_URI
                                , StationFactory.toContentValues(mStation))));

            } else {
                // If the station does exist in the db, we just use the copy in the DB
                mStation = StationFactory.fromCursor(fillupCheckCursor);
            }
            if(fillupCheckCursor != null){
                fillupCheckCursor.close();
            }

        }
    }


    public boolean validateInput(LinearLayout container){
        for(int i=0; i < container.getChildCount(); i++){
            View v = container.getChildAt(i);
            if(v instanceof TextInputLayout){
                View et = ((TextInputLayout) v).getChildAt(0);
                if(TextUtils.isEmpty(((EditText) et).getText().toString())
                        || ((EditText)et).getText().toString() == ""){
                    ((EditText) et).setHintTextColor(Color.RED);
                    ((EditText) et).setError(mContext.getResources()
                            .getString(R.string.edit_text_error));
                    return false;
                }
            }
        }
        mAddFillupView.buildFillup();
        if(!mIsEdit){
            mFillup.setStationId(mStation.getId());
            mFillup.setCarId(mCar.getId());
            mFillup.setDate(System.currentTimeMillis());
        }
        calculateFillupMpg();
        calculateAvgMpg();
        insertFillup();
        return true;
    }

    public void insertFillup(){
        if(mIsEdit){
            mContext.getContentResolver().update(DataContract.FillupTable.CONTENT_URI
                    , FillupFactory.toContentValues(mFillup)
                    , DataContract.FillupTable._ID + " = " + mFillup.getId()
                    , null);
        } else {
            mContext.getContentResolver().insert(DataContract.FillupTable.CONTENT_URI
                    , FillupFactory.toContentValues(mFillup));
        }
    }

    private void calculateFillupMpg(){
        //pull the fillup previous to this one, find the mileage difference and then divide the difference by the amount of fuel purchased at this fillup
        Cursor previousFillupCursor = mContext
                .getContentResolver()
                .query(DataContract.FillupTable.CONTENT_URI
                        , null
                        , DataContract.FillupTable.CAR + " = " + mCar.getId()
                        + " AND " + DataContract.FillupTable.DATE + " < " + mFillup.getDate()
                        , null
                        , " date DESC LIMIT 1");
        if(previousFillupCursor.moveToFirst()){
            Fillup previousFillup = FillupFactory.fromCursor(previousFillupCursor);
            mFillup.setFillupMpg((mFillup.getFillupMileage() - previousFillup.getFillupMileage())
                    / mFillup.getGallons());
            previousFillupCursor.close();
        } else{
            mFillup.setFillupMpg(0.00);
        }
    }

    private void calculateAvgMpg(){
        int fillupCount = -1;
        double mpgTotal = 0;
        Cursor allFillups = mContext.getContentResolver()
                .query(DataContract.FillupTable.CONTENT_URI
                        , null
                        , DataContract.FillupTable.CAR + " = " + mCar.getId()

                        , null, "date ASC");
        // fillup MPG is calculated by subtracting the previous fillup's mileage
        // from the current fillup's mileage, then dividing by
        // the gallons of fuel in this fillup
        if(allFillups != null && allFillups.moveToFirst()) {
            fillupCount += allFillups.getCount();

            while (allFillups.moveToNext()) {
                mpgTotal += allFillups.getInt(allFillups.getColumnIndexOrThrow(DataContract.FillupTable.MPG));
            }


            allFillups.close();
        } else {
            mFillup.setFillupMpg(0.0);
            mpgTotal = mFillup.getFillupMpg();
        }
        mCar.setAvgMpg(mpgTotal / fillupCount);
        mContext.getContentResolver().update(DataContract.CarTable.CONTENT_URI, CarFactory.toContentValues(mCar), DataContract.CarTable._ID + " = " + mCar.getId(), null);
    }

    public DatePickerFragment buildDatePickerFragment(){
        Calendar cal = Calendar.getInstance();
        if(mIsEdit) {
            cal.setTimeInMillis(mFillup.getDate());
        }
        DatePickerFragment datePickerFragment = new DatePickerFragment();
        Bundle data = new Bundle();
        data.putInt(KeyContract.YEAR, cal.get(Calendar.YEAR));
        data.putInt(KeyContract.MONTH, cal.get(Calendar.MONTH));
        data.putInt(KeyContract.DAY, cal.get(Calendar.DAY_OF_MONTH));
        datePickerFragment.setArguments(data);

        return datePickerFragment;
    }

    public void onDateSet(int year, int monthOfYear, int dayOfMonth) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, monthOfYear);
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        mFillup.setDate(cal.getTimeInMillis());
        mAddFillupView.setFields();
    }
}
