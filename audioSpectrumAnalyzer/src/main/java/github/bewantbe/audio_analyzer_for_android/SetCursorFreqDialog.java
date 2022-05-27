package github.bewantbe.audio_analyzer_for_android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import java.text.DecimalFormat;

public class SetCursorFreqDialog {
    private static final String TAG = "CursorFreqDialog:";

    private final AnalyzerActivity ct;
    private final AnalyzerGraphic graphView;
    private View setCursorFrequencyView;
    private EditText et_cursor_freq;
    private AlertDialog setCursorFreqDialog;

    SetCursorFreqDialog(AnalyzerActivity _ct, AnalyzerGraphic _graphView) {
        ct = _ct;
        graphView = _graphView;
       buildDialog(ct);
    }

    void ShowSetCursorFreqDialog() {
        Log.d(TAG, "ShowSetCursorFreqDialog(): SetCursorFreqDialog is not prepared.");

        double current_cursor_freq = graphView.getCursorFreq();
        DecimalFormat df = new DecimalFormat("#.##");
        ((EditText) setCursorFrequencyView.findViewById(R.id.et_cursor_freq)).setText(df.format(current_cursor_freq));
        setCursorFreqDialog.show();
    }

    private void buildDialog(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        setCursorFrequencyView = inflater.inflate(R.layout.dialog_set_cursor_freq, null);
        et_cursor_freq = setCursorFrequencyView.findViewById(R.id.et_cursor_freq);
        AlertDialog.Builder setCursorFreqBuilder = new AlertDialog.Builder(context);
        setCursorFreqBuilder
                .setView(setCursorFrequencyView)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        setCursorFrequencyView.findViewById(R.id.et_cursor_freq);
                        double freq = Double.parseDouble(et_cursor_freq.getText().toString());
                        graphView.setCursorFreq(freq);

                        // Save setting to preference, after sanitized.
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Log.v(TAG, "cursor frequency dialog: Canceled");
                    }
                });
//    freqDialogBuilder
//            .setTitle("dialog_title");
        setCursorFreqDialog = setCursorFreqBuilder.create();
    }
}
