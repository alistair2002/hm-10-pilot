package com.polkapolka.bluetooth.le;

/**
 * Created by jason on 13/07/15.
 */

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

//Widgets used

public class ScreenSlideCompass extends Fragment {
	OnFreeboardStringSend mCallback;
    private final static String TAG = ScreenSlideCompass.class.getSimpleName();
	private TextView textview;
	private EditText wantedBearing;

	// Container Activity must implement this interface
	public interface OnFreeboardStringSend {
		void onFreeboardString(String freeboardSentence);
	}

	public void setBearing(int bearing) { textview.setText(String.valueOf(bearing)); }
	public void setWanted(String wanted) { wantedBearing.setText( wanted ); }


	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try {
			mCallback = (OnFreeboardStringSend) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
										 + " must implement OnFreeboardStringSend");
		}
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
														  R.layout.bearing, container, false);
		Typeface tf= Typeface.createFromAsset(rootView.getContext().getAssets(), "digital-7 (mono).ttf");

		textview = (TextView) rootView.findViewById(R.id.bearing);
		textview.setTypeface(tf);

		wantedBearing = (EditText) rootView.findViewById(R.id.bearing_edit);

		wantedBearing.setOnEditorActionListener(
				new EditText.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						if (actionId == EditorInfo.IME_ACTION_SEARCH ||
								actionId == EditorInfo.IME_ACTION_DONE) {
							String edited = wantedBearing.getText().toString();
							Integer value = Integer.decode(edited);
							if (value < 0) value = 0;
							if (value > 360)
								value = value % 360;
							// the user is done typing.
							mCallback.onFreeboardString("#CWB:" + value + "\r\n");
						}


						return false; // pass on to other listeners.
					}
				});

		wantedBearing.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					wantedBearing.setText("");
				}
			});
		return rootView;
    }
}
