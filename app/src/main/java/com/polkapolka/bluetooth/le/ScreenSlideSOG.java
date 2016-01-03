package com.polkapolka.bluetooth.le;

/**
 * This code is inherited from the work of the the freeboard project
 * http://www.42.co.nz/freeboard/
 * and the HM10 bluetooth low energy module project
 * http://jnhuamao.cn/
 * Please acknowledge its origins if re-using it.  I add that any re-use is done so at your own risk etc..
 */

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

//Widgets used

public class ScreenSlideSOG extends Fragment {
    private OnFreeboardStringSend mCallback;
    private TextView textview;
    private EditText wantedEdit;

    // Container Activity must implement this interface
    public interface OnFreeboardStringSend {
        void onFreeboardString(String freeboardSentence);
    }

	public void setSOG(int value) { textview.setText(String.valueOf(value)); }


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
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.sog, container, false);

        Typeface tf= Typeface.createFromAsset(rootView.getContext().getAssets(), "digital-7 (mono).ttf");

        textview = (TextView) rootView.findViewById(R.id.sog);
        textview.setTypeface(tf);

        wantedEdit = (EditText) rootView.findViewById(R.id.edit);

        if (null != wantedEdit) {
            wantedEdit.setOnEditorActionListener(
                    new EditText.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                                    actionId == EditorInfo.IME_ACTION_DONE) {
                                String edited = wantedEdit.getText().toString();
                                // the user is done typing.
                                mCallback.onFreeboardString(edited + "\r\n");

                                wantedEdit.setText("");
                            }


                            return false; // pass on to other listeners.
                        }
                    });
        }


        wantedEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wantedEdit.setText("#PRO:2,#INT:5,#DER:1");
            }
        });
        return rootView;
    }
}
