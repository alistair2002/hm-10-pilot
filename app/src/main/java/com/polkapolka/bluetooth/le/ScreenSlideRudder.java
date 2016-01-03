package com.polkapolka.bluetooth.le;

import android.app.Activity;
import android.content.Context;
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

/**
 * This code is inherited from the work of the the freeboard project
 * http://www.42.co.nz/freeboard/
 * and the HM10 bluetooth low energy module project
 * http://jnhuamao.cn/
 * Please acknowledge its origins if re-using it.  I add that any re-use is done so at your own risk etc..
 */

public class ScreenSlideRudder extends Fragment {
    private OnFreeboardStringSend mCallback;
	private TextView textview;
	private EditText wantedRudder;

    // Container Activity must implement this interface
    public interface OnFreeboardStringSend {
        void onFreeboardString(String freeboardSentence);
    }

	public void setRudder(int rudder) { textview.setText(String.valueOf(rudder)); }
	public void setWanted(String wanted) { wantedRudder.setText( wanted ); }


	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		Activity activity;

		if (context instanceof Activity){
			activity=(Activity) context;
			// This makes sure that the container activity has implemented
			// the callback interface. If not, it throws an exception
			try {
				mCallback = (OnFreeboardStringSend) activity;
			} catch (ClassCastException e) {
				throw new ClassCastException(activity.toString()
						+ " must implement OnFreeboardStringSend");
			}
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		ViewGroup rootView = (ViewGroup) inflater.inflate(
														  R.layout.rudder, container, false);

		Typeface tf= Typeface.createFromAsset(rootView.getContext().getAssets(), "digital-7 (mono).ttf");

		textview = (TextView) rootView.findViewById(R.id.rudder);
		textview.setTypeface(tf);

		wantedRudder = (EditText) rootView.findViewById(R.id.rudder_edit);

		wantedRudder.setOnEditorActionListener( 
												new EditText.OnEditorActionListener() {
													@Override
													public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
														if (actionId == EditorInfo.IME_ACTION_SEARCH ||
															actionId == EditorInfo.IME_ACTION_DONE) {
															String edited = wantedRudder.getText().toString();
															Integer value = Integer.decode(edited);
															if (value < -35) value = 35;
															if (value > 35) value = 35;
																																													// the user is done typing.
															mCallback.onFreeboardString("#RWA:"+value+"\r\n");
														}


														return false; // pass on to other listeners.
													}
												});

		wantedRudder.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				wantedRudder.setText("");
			}
		});
		
		return rootView;
	}
}
