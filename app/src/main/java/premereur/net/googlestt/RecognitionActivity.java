package premereur.net.googlestt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class RecognitionActivity extends Activity {
    private static final String TAG = "RECOG";
    private final static String[] TARGET_TEXTS = {
            "next corner",
            "extend corner",
            "next item",
            "repeat item",
            "detail",
            "detail",
    };

    @Bind(R.id.targetTxt)
    TextView targetTxt;
    @Bind(R.id.recognisedTxt)
    TextView recognisedTxt;
    @Bind(R.id.resultTxt)
    TextView resultTxt;

    private long lastTargetTime = 0;
    private int numHits = 0;
    private int numMisses = 0;
    private int numErrors = 0;
    private int numNoMatch = 0;
    private int numTimeout = 0;
    private String target;

    private SpeechRecognizer speechRecognizer = null;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);
        ButterKnife.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastTargetTime = System.currentTimeMillis();
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.ERROR) {
                    Log.e(TAG, "TTS error " + status);
                } else {
                    tts.setLanguage(Locale.UK);
                    nextSentence();
                }
            }
        });
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {

            }

            @Override
            public void onBufferReceived(byte[] buffer) {

            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "End of speech");
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "Error " + error);
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        numNoMatch += 1;
                        recognisedTxt.setText("no match");
                        showStats();
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        numTimeout += 1;
                        recognisedTxt.setText("timeout");
                        showStats();
                        break;
                    default:
                        numErrors += 1;
                        recognisedTxt.setText("Other error: " + error);
                        showStats();
                }
                nextSentence();
            }

            @Override
            public void onResults(Bundle results) {
                final Object recognitionResults = results.get(SpeechRecognizer.RESULTS_RECOGNITION);
                if (recognitionResults != null && recognitionResults instanceof List) {
                    final List<String> matches = (List<String>) recognitionResults;
                    Log.d(TAG, "results: " + matches.toString());
                    final String match = analyze(matches);
                    if (target.equals(match)) {
                        numHits += 1;
                    } else {
                        numMisses += 1;
                    }
                    recognisedTxt.setText(target + " ---> " + match);
                    showStats();
                }
                nextSentence();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                Log.d(TAG, "partial: " + partialResults.get(SpeechRecognizer.RESULTS_RECOGNITION));
            }

            @Override
            public void onEvent(int eventType, Bundle params) {

            }
        });
        Log.d(TAG, "Started listening for speech");
    }

    @OnClick(R.id.resetBtn)
    public void reset(final Button button) {
        numHits = numMisses = numNoMatch = numTimeout = numErrors = 0;
        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                nextSentence();
            }
        });
    }

    private void showStats() {
        resultTxt.setText(String.format(
                "%d/%d/%d/%d/%d",
                numHits, numMisses, numNoMatch, numTimeout, numErrors));
    }

    private String analyze(final List<String> sources) {
        final Set<String> sourceWords = new HashSet<>();
        for (final String source : sources) {
            Collections.addAll(sourceWords, source.split("(\\s|\\W)"));
        }
        if (containsWordLike(sourceWords, "next", "skip")) {
            if (containsWordLike(sourceWords, "corner", "section")) {
                return "next corner";
            }
            return "next item";
        }
        if (containsWordLike(sourceWords, "repeat", "again", "extend")) {
            if (containsWordLike(sourceWords, "corner", "section")) {
                return "extend corner";
            }
            return "repeat item";
        }
        if (containsWordLike(sourceWords, "detail")) {
            return "detail";
        }
        return "";
    }

    private boolean containsWordLike(final Set<String> sourceWords, final String... targets) {
        for (final String sourceWord : sourceWords) {
            for (final String target : targets) {
                if (sourceWord.contains(target)) {
                    return true;
                }
            }
        }
        return false;
    }


    private void nextSentence() {
        if (numHits + numMisses + numErrors + numTimeout + numNoMatch < 50) {
            final long elapsed = System.currentTimeMillis() - lastTargetTime;
            Log.v(TAG, "Elapsed = " + elapsed);
            if ( elapsed < 5000 ) {
                new Handler(getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        nextSentence();
                    }
                }, 5000 - elapsed);
            } else {
                lastTargetTime = System.currentTimeMillis();
                target = random(TARGET_TEXTS);
                targetTxt.setText(target);
                speakCommandTarget(target);
                //listenForCommand();
            }
        } else {
            targetTxt.setText("The end");
        }
    }

    private void speakCommandTarget(final String target) {
        Log.d(TAG, "speak command " + target);
        tts.speak("Say: " + target, TextToSpeech.QUEUE_FLUSH, null, null);
        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                listenForCommand();
            }
        }, 1200);
    }

    private void listenForCommand() {
        Log.v(TAG, "listen for command");
        final Intent recognizerIntent = new Intent();
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.setAction(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        speechRecognizer.startListening(recognizerIntent);
    }

    private String random(String[] texts) {
        return texts[new Random().nextInt(texts.length)];
    }

    @Override
    protected void onPause() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onPause();
    }
}
