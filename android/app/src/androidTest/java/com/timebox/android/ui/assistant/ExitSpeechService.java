package com.timebox.android.ui.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Collections;

/** Runs in the test APK's own process, without the target APK's Kotlin runtime. */
public class ExitSpeechService extends RecognitionService {
    @Override protected void onStartListening(Intent intent, Callback callback) {
        try { callback.readyForSpeech(new Bundle()); }
        catch (RemoteException error) { throw new RuntimeException(error); }
    }
    @Override protected void onCancel(Callback callback) {}
    @Override protected void onStopListening(Callback callback) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Bundle result = new Bundle();
            result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,
                new ArrayList<>(Collections.singletonList("exit regression spoken activity")));
            try { callback.results(result); }
            catch (RemoteException error) { throw new RuntimeException(error); }
        }, 500);
    }
}
