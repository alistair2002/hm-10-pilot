package com.polkapolka.bluetooth.le;

import android.app.Fragment;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;


/**
 * A placeholder fragment containing a simple view.
 */
public class CompassActivityFragment extends Fragment
	implements SensorEventListener {

    // device sensor manager
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;


    // define the display assembly compass picture
    private ImageView compass_image;
    private ImageView boat_image;

    // record the compass picture angle turned
    private float currentCompass = 0f;
    private float currentBoat = 0f;
    private float wantedBoat = 0f;

	// do we need all of these?
	private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];
    //private float mCurrentDegree = 0f;

    public CompassActivityFragment() {
    }

	public void setWantedBoat(float whereTheBoatPointsNow)
	{
		wantedBoat = whereTheBoatPointsNow;
	}
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
		View compassView = inflater.inflate(R.layout.fragment_compass, container, false);

        // initialize your android device sensor capabilities
        mSensorManager = (SensorManager) compassView.getContext().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer =  mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // our compass image
        compass_image = (ImageView) compassView.findViewById(R.id.compass);
        boat_image = (ImageView) compassView.findViewById(R.id.boat);

		return compassView;
    }

	@Override
    public void onResume() {
        super.onResume();

        // for the system's orientation sensor registered listeners
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();

        // to stop the listener and save battery
        mSensorManager.unregisterListener(this, mAccelerometer);
        mSensorManager.unregisterListener(this, mMagnetometer);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor == mAccelerometer) {
            System.arraycopy(event.values, 0, mLastAccelerometer, 0, event.values.length);
            mLastAccelerometerSet = true;
        } else if (event.sensor == mMagnetometer) {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0, event.values.length);
            mLastMagnetometerSet = true;
        }
        if (mLastAccelerometerSet && mLastMagnetometerSet) {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnetometer);
            SensorManager.getOrientation(mR, mOrientation);
            float azimuthInRadians = mOrientation[0];
            float azimuthInDegrees = ((float)(-1f * Math.toDegrees(azimuthInRadians))+360)%360;

            if (Math.abs(currentCompass - azimuthInDegrees) > 180) {
                if (currentCompass > azimuthInDegrees){
                    currentCompass -= 360;
                }
                else {
                    azimuthInDegrees -= 360;
                }
            }
            // low pass filter of 1/3
            azimuthInDegrees = (2*currentCompass + azimuthInDegrees)/3;

            float boatInDegrees = (azimuthInDegrees + wantedBoat) %360;
            RotateAnimation ra = new RotateAnimation(currentCompass, azimuthInDegrees,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f );
            RotateAnimation rb = new RotateAnimation( currentBoat, boatInDegrees,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f );

            ra.setDuration(100);
            rb.setDuration(50);

            ra.setFillAfter(true);
            rb.setFillAfter(true);

            compass_image.startAnimation(ra);
            boat_image.startAnimation(rb);

            currentCompass = azimuthInDegrees;
            currentBoat = boatInDegrees;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }
}
