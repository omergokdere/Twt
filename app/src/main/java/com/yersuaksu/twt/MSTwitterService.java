package com.yersuaksu.twt;


import java.io.File;

import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;

/**
 * Handler for all background processes in MSTweet process.
 * @author MindSpiker
 */
public class MSTwitterService extends IntentService {

	/* message interface */

    /***** messages passed to MSTwitterService *********************/

    /** Passed to MSTwitterService to hold value indicating what action to take */
    protected static final String MST_KEY_SERVICE_TASK = "mstKeyServiceTask";

    /** Passed to MSTwitterService in MST_KEY_SERVICE_TASK indicating it should get the authorization url */
    protected static final int MST_SERVICE_TASK_GET_AUTH_URL = 1;
    /** Passed to MSTwitterService in MST_KEY_SERVICE_TASK indicating it should send a tweet */
    protected static final int MST_SERVICE_TASK_SENDTWEET = 2;
    /** Passed to MSTwitterService in MST_KEY_SERVICE_TASK indicating it should make an access token */
    protected static final int MST_SERVICE_TASK_MAKE_TOKEN = 3;

    /** Passed to MSTwitterService, holds the tweet text (duh!)  */
    protected static final String MST_KEY_TWEET_TEXT = "mstKeyTweetText";
    /** Passed to MSTwitterService, holds the tweet image path (duh!)  */
    protected static final String MST_KEY_TWEET_IMAGE_PATH = "mstKeyTweetImagePath";

    /** passed to MSTwitterService, hold oauth verifier string (duh!) */
    protected static final String MST_KEY_AUTH_OAUTH_VERIFIER = "mstKeyAuthOAuthVerifier";


    /***** messages returned from MSTwitterService ******************/

    /** Passed back int result code from MSTwitterService to communicate upload result boolean*/
    protected static final String MST_KEY_SENDTWEET_RESULT = "mstKeySendTweetResult";

    /** Passed back from MSTwitterService to communicate authorization url String */
    protected static final String MST_KEY_AUTHURL_RESULT = "mstKeyAuthURLResult";
    /** Passed back from MSTwitterService to communicate url String */
    protected static final String MST_KEY_AUTHURL_RESULT_URL = "mstKeyAuthURLResultURL";

    /** Passed back from MSTwitterService to communicate authorization url String */
    protected static final String MST_KEY_MAKE_TOKEN_RESULT = "mstKeyMakeTokenResult";

    private String mText; //tweet text
    private String mImagePath; //tweet image path

    public MSTwitterService() {
        super("");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // assemble data
        Bundle extras = intent.getExtras();

        if (extras.containsKey(MSTwitterService.MST_KEY_SERVICE_TASK)) {

            parseTweetTextAndPath(extras);

            int task = extras.getInt(MSTwitterService.MST_KEY_SERVICE_TASK);
            switch (task) {
                case MSTwitterService.MST_SERVICE_TASK_GET_AUTH_URL:
                    processGetAuthURL();
                    break;
                case MSTwitterService.MST_SERVICE_TASK_SENDTWEET:
                    processSendTweet(extras);
                    break;
                case MSTwitterService.MST_SERVICE_TASK_MAKE_TOKEN:
                    processMakeToken(extras);
                    break;
            }
        }
    }

    /**
     * Used to parse the tweet text and image path which get passed to and from each task
     * @param extras
     */
    private void parseTweetTextAndPath(Bundle extras) {
        mText = null;
        if (extras.containsKey(MSTwitterService.MST_KEY_TWEET_TEXT)) {
            mText = extras.getString(MSTwitterService.MST_KEY_TWEET_TEXT);
        }
        mImagePath = null;
        if (extras.containsKey(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH)) {
            mImagePath = extras.getString(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH);
        }
    }

    /**
     * Get the authorization URL and send it back in a sticky broadcast.
     */
    private void processGetAuthURL() {

        //create a RequestToken to use to create the request URL
        // token will be used later to decode result from twitter.com
        // and needs to be saved to static variable in MSTwitter
        RequestToken reqToken = null;
        Twitter twitter4j = null;
        String url = null;
        int resultCode = MSTwitter.MST_RESULT_SUCCESSFUL;	// be optimistic

        try {
            twitter4j = new TwitterFactory().getInstance();
            twitter4j.setOAuthConsumer(MSTwitter.smConsumerKey, MSTwitter.smConsumerSecret);
        } catch (IllegalStateException e) {
            // No network access or token already available
            resultCode = MSTwitter.MST_RESULT_ILLEGAL_STATE_SETOAUTHCONSUMER;
            Log.e(MSTwitter.TAG, e.toString());
        }

        // get the token
        if (resultCode == MSTwitter.MST_RESULT_SUCCESSFUL) {
            try {
                reqToken = twitter4j.getOAuthRequestToken(MSTwitter.CALLBACK_URL);
            } catch (TwitterException e) {
                int tErrorNum = MSTwitter.getTwitterErrorNum(e, this);
                // No network access
                resultCode = tErrorNum;
                Log.e(MSTwitter.TAG, e.getExceptionCode() + ": " + e.getMessage());
            } catch (IllegalStateException e) {
                // No network access or token already available
                resultCode = MSTwitter.MST_RESULT_ILLEGAL_STATE_TOKEN_ALREADY_AVALIABLE;
                Log.e(MSTwitter.TAG, e.toString());
            }
        }

        // if we got the request token then use it to get the url
        if ( resultCode == MSTwitter.MST_RESULT_SUCCESSFUL) {
            url = reqToken.getAuthenticationURL();
            // save the request token
            MSTwitter.smReqToken = reqToken;
        }

        // broadcast the results
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MSTwitter.INTENT_BROADCAST_MSTWITTER);
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_SERVICE_TASK, MSTwitterService.MST_SERVICE_TASK_GET_AUTH_URL);
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_AUTHURL_RESULT, resultCode);
        if (url != null) {
            broadcastIntent.putExtra(MSTwitterService.MST_KEY_AUTHURL_RESULT_URL, url);
        }
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);
        sendStickyBroadcast(broadcastIntent);
    }

    /**
     * Make the token and save using SharedPreferences. Then send back a sticky broadcast
     * @param extras Bundle containing the oauth verifier string
     */
    private void processMakeToken(Bundle extras) {
        AccessToken accessToken = null;
        int resultCode = MSTwitter.MST_RESULT_NO_PASSED_OAUTH;
        // get the oAuth verifier string from the bundle
        String oAuthVerifier = extras.getString(MST_KEY_AUTH_OAUTH_VERIFIER);
        if (oAuthVerifier != null) {
            // first setup the twitter4j object
            resultCode = MSTwitter.MST_RESULT_SUCCESSFUL;
            Twitter twitter4j = null;
            try {
                twitter4j = new TwitterFactory().getInstance();
                twitter4j.setOAuthConsumer(MSTwitter.smConsumerKey, MSTwitter.smConsumerSecret);
            } catch (IllegalStateException e) {
                // No network access or token already available
                resultCode = MSTwitter.MST_RESULT_ILLEGAL_STATE_SETOAUTHCONSUMER;
                Log.e(MSTwitter.TAG, e.toString());
            }

            // now get the access token
            if (resultCode == MSTwitter.MST_RESULT_SUCCESSFUL) {
                try {
                    accessToken = twitter4j.getOAuthAccessToken(MSTwitter.smReqToken, oAuthVerifier);
                } catch (NullPointerException e) {
                    resultCode = MSTwitter.MST_RESULT_BAD_RESPONSE_FROM_TWITTER;
                    Log.e(MSTwitter.TAG, e.toString());
                } catch (UnsupportedOperationException e) {
                    resultCode = MSTwitter.MST_RESULT_BAD_RESPONSE_FROM_TWITTER;
                    Log.e(MSTwitter.TAG, e.toString());
                } catch (TwitterException e) {
                    resultCode = MSTwitter.getTwitterErrorNum(e, this);
                    Log.e(MSTwitter.TAG, e.getLocalizedMessage());
                }
            }
        }

        if (accessToken != null) {
            // save the access token parts
            String token = accessToken.getToken();
            String secret = accessToken.getTokenSecret();

            // Create shared preference object to remember if the user has already given us permission
            SharedPreferences refs = this.getSharedPreferences(MSTwitter.PERF_FILENAME, Context.MODE_PRIVATE);
            Editor editor = refs.edit();
            editor.putString(MSTwitter.PREF_ACCESS_TOKEN, token);
            editor.putString(MSTwitter.PREF_ACCESS_TOKEN_SECRET, secret);
            editor.commit();
            resultCode = MSTwitter.MST_RESULT_SUCCESSFUL;
        }

        // broadcast the results
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MSTwitter.INTENT_BROADCAST_MSTWITTER);
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_SERVICE_TASK, MSTwitterService.MST_SERVICE_TASK_MAKE_TOKEN);
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_MAKE_TOKEN_RESULT, resultCode);
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
        broadcastIntent.putExtra(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);
        sendStickyBroadcast(broadcastIntent);
    }

    /**
     * Assemble the data and call sendTweet(). Then send a sticky broadcast with results.
     * @param extras Bundle containing the tweet text and image
     */
    private void processSendTweet(Bundle extras){

        AccessToken accessToken = MSTwitter.getAccessToken(this);

        // do the tweet
        int resultCode = sendTweet(mText, mImagePath, accessToken);

        // broadcast the results
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MSTwitter.INTENT_BROADCAST_MSTWITTER);
        broadcastIntent.putExtra(MST_KEY_SERVICE_TASK, MST_SERVICE_TASK_SENDTWEET);
        broadcastIntent.putExtra(MST_KEY_SENDTWEET_RESULT, resultCode);
        sendStickyBroadcast(broadcastIntent);
    }

    /**
     * Sets access token, sends tweet.
     * @category Helpers
     * @return result code
     */
    private int sendTweet(String text, String imagePath, AccessToken accessToken) {
        int resultCode = MSTwitter.MST_RESULT_SUCCESSFUL;

        // check to make sure we have data and access before tweeting
        if (text == null && imagePath == null) {
            return MSTwitter.MST_RESULT_NO_DATA_TO_SEND;
        }
        if (accessToken == null) {
            return MSTwitter.MST_RESULT_NOT_AUTHORIZED;
        }

        // get twitter4j object
        Twitter twitter4j = null;
        try {
            twitter4j = new TwitterFactory().getInstance();
            twitter4j.setOAuthConsumer(MSTwitter.smConsumerKey, MSTwitter.smConsumerSecret);
        } catch (IllegalStateException e) {
            // No network access or token already available
            resultCode = MSTwitter.MST_RESULT_ILLEGAL_STATE_SETOAUTHCONSUMER;
            Log.e(MSTwitter.TAG, e.toString());
            return resultCode;
        }

        // Create and set twitter access credentials from token and or secret
        twitter4j.setOAuthAccessToken(accessToken);
        try {
            // finally update the status (send the tweet)
            StatusUpdate status = new StatusUpdate(text);
            if (imagePath != null) {
                status.setMedia(new File(imagePath));
            }
            twitter4j.updateStatus(status);
        } catch (TwitterException e) {
            return MSTwitter.getTwitterErrorNum(e, this);
        }

        return resultCode;
    }
}


