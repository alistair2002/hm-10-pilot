package com.polkapolka.bluetooth.le;

/**
 * This code is inherited from the work of the the freeboard project
 * http://www.42.co.nz/freeboard/
 * and the HM10 bluetooth low energy module project
 * http://jnhuamao.cn/
 * Please acknowledge its origins if re-using it.  I add that any re-use is done so at your own risk etc..
 */

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

//Widgets used

public class ScreenSlideCOG extends Fragment {
    //private final static String TAG = ScreenSlideCOG.class.getSimpleName();
    private TextView textview;

	public void setCOG(int value) { textview.setText(String.valueOf(value)); }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.cog, container, false);

        Typeface tf= Typeface.createFromAsset(rootView.getContext().getAssets(), "digital-7 (mono).ttf");

        textview = (TextView) rootView.findViewById(R.id.cog);
        textview.setTypeface(tf);

        return rootView;
    }
}
