package com.yersuaksu.twt;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MSTwitterAuthorizer extends Activity {

    private String mURL;

	/* message interface */

    /** sent to MSTwitterAuthorizer to pass in the url to open */
    protected static String MST_KEY_AUTH_URL = "mstKeyAuthURL";

    /** returned from MSTwitterAuthorizer to use to create auth credentials */
    protected static String MST_KEY_AUTH_OAUTH_VERIFIER = "mstKeyAuthOAuthVerifier";

    private String mText; //tweet text
    private String mImagePath; //tweet image path

    /**
     * @category ActivityEvents
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get the new or saved bundle to get the url
        Bundle bundle = savedInstanceState;
        if (bundle ==  null) {
            bundle = this.getIntent().getExtras();
        }
        String url = loadURL(bundle);
        if (url == null) {
            setResult(MSTwitter.MST_RESULT_NO_PASSED_URL);
            finish();
        }

        mURL = url;	// will be saved in onSaveInstanceState()

        // create WebView and set as content view
        WebView twitterSite = new WebView(this);
        twitterSite.setWebViewClient(new  TwitterWebViewClient());
        twitterSite.getSettings().setJavaScriptEnabled(true);
        twitterSite.loadUrl(url);
        this.setContentView(twitterSite);

        // set result to canceled in case activity gets canceled unexpectedly
        setResult(MSTwitter.MST_RESULT_USER_CANCELED);

        // get and save text and imagepath
        mText = null;
        if (bundle.containsKey(MSTwitterService.MST_KEY_TWEET_TEXT)) {
            mText = bundle.getString(MSTwitterService.MST_KEY_TWEET_TEXT);
        }
        mImagePath = null;
        if (bundle.containsKey(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH)) {
            mImagePath = bundle.getString(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH);
        }
    }

    private String loadURL(Bundle bundle) {
        String outS = null;
        if (bundle != null) {
            if (bundle.containsKey(MST_KEY_AUTH_URL)) {
                outS = bundle.getString(MST_KEY_AUTH_URL);
            }
        }
        return outS;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // save url in case something happens to pause exicution of this activity
        outState.putString(MST_KEY_AUTH_URL, mURL);
        outState.putString(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
        outState.putString(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);
    }

    /**
     * @category Helpers
     * Get the oauth verifier string from the returned url and send
     * it back in the result intent
     *
     * @param url passed back from Twitter.com
     */
    private void processResponse(String url) {

        // get the oAuth string used to make the access token parts
        String oAuthVerifier = null;
        try {
            Uri uri = Uri.parse(url);
            oAuthVerifier = uri.getQueryParameter("oauth_verifier");
        } catch (NullPointerException e) {
            setResult(MSTwitter.MST_RESULT_BAD_RESPONSE_FROM_TWITTER);
            Log.e(MSTwitter.TAG, e.toString());
            return;
        } catch (UnsupportedOperationException e) {
            setResult(MSTwitter.MST_RESULT_BAD_RESPONSE_FROM_TWITTER);
            Log.e(MSTwitter.TAG, e.toString());
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra(MST_KEY_AUTH_OAUTH_VERIFIER, oAuthVerifier);

        resultIntent.putExtra(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
        resultIntent.putExtra(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);

        setResult(MSTwitter.MST_RESULT_SUCCESSFUL, resultIntent);
    }

    /**
     * Used to capture change url events of the webview. By capturing this event
     * the returned url from twitter can be obtained. This url contains the
     * oauth verfier string we are aiming to get from this part of the process.
     * @category Objects
     */
    private class TwitterWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // see if Twitter.com send back a url containing the callback key
            if (url.contains(MSTwitter.CALLBACK_URL)) {
                // we are done talking with twitter.com so check credentials and finish interface.
                processResponse(url);
                finish();
                return true;
            } else {
                // keep browsing
                view.loadUrl(url);
                return false;
            }
        }
    }
}


