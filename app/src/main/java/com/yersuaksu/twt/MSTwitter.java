package com.yersuaksu.twt;


import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import twitter4j.TwitterFactory;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

/**
 * Encapsulates posting a tweet using twitter4j.
 * @author MindSpiker
 */
public class MSTwitter {
    protected static final String INTENT_BROADCAST_MSTWITTER = "com.mindspiker.MSTwitter.broadcast";
    /** used to detect twitter result in onActivityResult from MSTwitterAuthorizer */
    private static final int REQUEST_TWITTER_AUTH = 544;

    /** used in log statements */
    protected static final String TAG = "MSTwitter";

    /** File name of preferences file used to store authentication details */
    protected static final String PERF_FILENAME = "MSPerfsFile";
    /** Name to store the users access token */
    protected static final String PREF_ACCESS_TOKEN = "accessToken";
    /** Name to store the users access token secret */
    protected static final String PREF_ACCESS_TOKEN_SECRET = "accessTokenSecret";
    /** String with last unknown error that resulted from a twitter operation **/
    protected static final String PREF_LAST_TWITTER_ERROR = "lastTwitterError";
    /** The URL that Twitter will redirect to after a user log's in - this will be picked up by the custom webView */
    protected static final String CALLBACK_URL = "com-mindspiker-mstwitter";

    /** Tweet Life Cycle events are returned to calling activity via MSTwitterResultReceiver */
    /** Tweet life cycle event status: Means app is being authorized */
    public static final int MSTWEET_STATUS_AUTHORIZING = 1;
    /** Tweet life cycle event status: Means app is authorized and tweet is being uploaded */
    public static final int MSTWEET_STATUS_STARTING = 2;
    /** Tweet life cycle event status: Means tweet uploaded successfully */
    public static final int MSTWEET_STATUS_FINSIHED_SUCCCESS = 3;
    /** Tweet life cycle event status: Means tweet unsuccessful */
    public static final int MSTWEET_STATUS_FINSIHED_FAILED = 4;

    /** Consumer Key String from http://dev.twitter.com/apps/ only one per app */
    protected static String smConsumerKey;
    /** Consumer Secret String from http://dev.twitter.com/apps/ only one per app */
    protected static String smConsumerSecret;
    /** Request token signifies the unique ID of the request you are sending to twitter */
    protected static RequestToken smReqToken;

    /**
     * Sticky broadcasts persist and any prior broadcast will trigger in the
     * broadcast receiver as soon as it is registered.
     * To clear any prior broadcast this code sends a blank broadcast to clear
     * the last sticky broadcast.
     * This broadcast has no extras it will be ignored in the broadcast receiver
     */
    private static void clearPriorBroadcast(Context context){
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MSTwitter.INTENT_BROADCAST_MSTWITTER);
        context.sendStickyBroadcast(broadcastIntent);
    }

    /**
     * Get the access token stored in the shared preferences file
     * @param context A contect that has access to the shared file
     * @return
     */
    protected static AccessToken getAccessToken(Context context) {
        // Create shared preference object to remember if the user has already given us permission
        SharedPreferences prefs = context.getSharedPreferences(PERF_FILENAME, Context.MODE_PRIVATE);

        // do we already have an access credentials saved from a previous tweet?
        if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.contains(PREF_ACCESS_TOKEN_SECRET)) {
            // set access token using saved token and secret values
            String token = prefs.getString(PREF_ACCESS_TOKEN, null);
            String secret = prefs.getString(PREF_ACCESS_TOKEN_SECRET, null);
            return new AccessToken(token, secret);
        } else {
            return null;
        }
    }

    /**
     * @param context
     * @return true if this app is authorized to post tweets
     */
    public static boolean authorized(Context context) {
        AccessToken token = getAccessToken(context);
        if (token == null){
            return false;
        } else {
            return true;
        }
    }

    /**
     * @category Helpers
     * Write bitmap associated with a url to disk cache
     * @param context - Context used to get the app cache directory
     * @param image - Bitmap of file to save
     * @return String - file path of image saved
     */
    public static String putBitmapInDiskCache(Context context, Bitmap image) {
        // Create a path pointing to the system-recommended cache dir for the app
        String filename = "testrgfg.png";
        File cacheFile= new File(context.getCacheDir(), filename);
        try {
            // Create a file at the file path, and open it for writing obtaining the output stream
            cacheFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(cacheFile);
            // Write the bitmap to the output stream (and thus the file)
            // in PNG format (lossless compression)
            image.compress(CompressFormat.PNG, 1, fos);
            // Flush and close the output stream
            fos.flush();
            fos.close();
            return cacheFile.getPath();
        } catch (Exception e) {
            // Log anything that might go wrong with IO to file
            Log.e(TAG, "Error when saving image to cache. ", e);
            return null;
        }
    }

    /**
     * @category Helpers
     * @param context - Context used to get the app cache directory
     * @param filename - filename of the file to open
     * @return Bitmap - object of the opened file.
     */
    public static Bitmap getBitmap(Context context, String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            Bitmap outBitmap = BitmapFactory.decodeFile(filePath);
            return outBitmap;
        } else {
            return null;
        }
    }

    /**
     * Used to receive messages from MSTwitter during the tweet process
     */
    public interface MSTwitterResultReceiver {
        public void onRecieve(int tweetLifeCycleEvent, String tweetMessage);
    }

    /**
     * Receives boradcast events from MSTwitterService
     */
    protected class MSTwitterSerciceBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            processRecievedEvent(extras);
        }
    }

    //*******************Error handling ************************
    /** result codes sent between intents */
    protected static final int
            MST_RESULT_USER_CANCELED = 0,	// default
            MST_RESULT_SUCCESSFUL = 1,
            MST_RESULT_NOT_AUTHORIZED = 401,
            MST_RESULT_DUPLICATE_STATUS = 403,
            MST_RESULT_RATE_LIMITED = 420,
            MST_RESULT_SERVERS_OVERLOADED = 503,
            MST_RESULT_TWITTER_NOT_AVALIABLE = 4,
            MST_RESULT_ACCESS_TOKEN_EXISTS = 5,
            MST_RESULT_BAD_RESPONSE_FROM_TWITTER = 6,
            MST_RESULT_NO_PASSED_URL = 7,
            MST_RESULT_NO_PASSED_OAUTH = 8,
            MST_RESULT_INVALID_CREDENTIALS = 9,
            MST_RESULT_ILLEGAL_STATE_TOKEN_ALREADY_AVALIABLE = 10,
            MST_RESULT_ILLEGAL_STATE_SETOAUTHCONSUMER = 11,
            MST_RESULT_NO_DATA_TO_SEND = 12,
            MST_RESULT_UNKNOWN_ERROR = 13;

    /**
     * Returns a result text from a result code.
     * @param resultCode
     * @return String containing result text.
     */
    protected static String getResultDescription(int resultCode, Context context) {
        switch (resultCode) {
            case MST_RESULT_USER_CANCELED:
                return "Canceled by user or OS.";
            case MST_RESULT_SUCCESSFUL:
                return "Success.";
            case MST_RESULT_NOT_AUTHORIZED:
                return "Denied by Twitter.com: Unauthorized. 401";
            case MST_RESULT_DUPLICATE_STATUS:
                return "Denied by Twitter.com: duplicate status or update limits. 403";
            case MST_RESULT_RATE_LIMITED:
                return "Denied by Twitter.com: rate limited. Enhance your calm. 420";
            case MST_RESULT_SERVERS_OVERLOADED:
                return "Denied by Twitter.com: Servers overloaded. 503";
            case MST_RESULT_TWITTER_NOT_AVALIABLE:
                return "Twitter service or network is unavailable.";
            case MST_RESULT_ACCESS_TOKEN_EXISTS:
                return "Access token is already available.";
            case MST_RESULT_BAD_RESPONSE_FROM_TWITTER:
                return "Response from Twitter.com is wrong uri format.";
            case MST_RESULT_NO_PASSED_URL:
                return "Twitter authorization url not found.";
            case MST_RESULT_NO_PASSED_OAUTH:
                return "Twitter oauth verifier url not found.";
            case MST_RESULT_INVALID_CREDENTIALS:
                return "Invalid app credentials: Consumer Key, Consumer Secret, or both.";
            case MST_RESULT_ILLEGAL_STATE_TOKEN_ALREADY_AVALIABLE:
                return "Illegal state exception getting request token. Twitter access token already available.";
            case MST_RESULT_NO_DATA_TO_SEND:
                return "No data to send.";
            case MST_RESULT_ILLEGAL_STATE_SETOAUTHCONSUMER:
                return "Illegal state when setting consumer key and secret. OAuth consumer has already been set, or the instance is using basic authorization.";

            case MST_RESULT_UNKNOWN_ERROR:
            default:
                String lastErrorS = getLastTwitterError(context);
                if (lastErrorS == null) {
                    return "Unknown error code: " + resultCode;
                } else {
                    return lastErrorS;
                }
        }
    }

    /**
     * Get the last twitter error stored in the shared preferences file
     * @param context A context that has access to the shared file
     * @return String in perfs file or null
     */
    protected static String getLastTwitterError(Context context) {
        // Create shared preference object to remember if the user has already given us permission
        SharedPreferences prefs = context.getSharedPreferences(PERF_FILENAME, Context.MODE_PRIVATE);

        // do we already have an access credentials saved from a previous tweet?
        if (prefs.contains(PREF_LAST_TWITTER_ERROR)) {
            // set access token using saved token and secret values
            return prefs.getString(PREF_LAST_TWITTER_ERROR, null);
        } else {
            return null;
        }
    }

    /**
     * Parse result code and message to get twitter error number
     * @return int with MST_RESULT code.
     */
    protected static int getTwitterErrorNum(TwitterException e, Context context) {
        int errNum = e.getErrorCode();
        String errMsg = e.getMessage();

        if (errNum != -1) {
            // get error code from message sent back from twitter
            String errNumS = e.getMessage().substring(0, 3);
            Log.d(MSTwitter.TAG, "errNumS="+errNumS);
            try {
                errNum = Integer.parseInt(errNumS);
            } catch (NumberFormatException nfe) {
                errNum = -1;
            }
        }

        int outInt = -1;
        switch (errNum) {
            case 401:
            case 403:
            case 420:
            case 503:
                outInt = errNum;
                break;
            default:
                // first see if it is from bad creditentials
                if (errMsg.equals("Received authentication challenge is null")){
                    outInt = MST_RESULT_INVALID_CREDENTIALS;
                } else {
                    // Create shared preference object to remember this error message.
                    SharedPreferences refs = context.getSharedPreferences(MSTwitter.PERF_FILENAME, Context.MODE_PRIVATE);
                    Editor editor = refs.edit();
                    editor.putString(MSTwitter.PREF_LAST_TWITTER_ERROR, errMsg + "[code:"+e.getErrorCode()+"]");			editor.commit();
                    outInt = MST_RESULT_UNKNOWN_ERROR;
                }
        }
        Log.e(MSTwitter.TAG, "Tweeter Error: " + e.getMessage());

        return outInt;
    }

    ////////////////////////non static class starts here /////////////////////////////

    private MSTwitterResultReceiver mResultReceiver;
    private Activity mCallingActivity;
    private boolean mDoingATweet;
    private String mText;
    private String mImagePath;

    /**
     * Constructor. Should be placed in onCreate of calling activity. Is OK if this object gets destroyed
     * and recreated during a tweet. In other words there is no need to pass it around in the
     * savedInstanceState bundle.
     * @param activity Calling activity used to perform QUI thread activities, required will throw err if null.
     * @param consumerKey Required String value provided by https://dev.twitter.com/apps/
     * @param consumerSecret Required String value provided by https://dev.twitter.com/apps/
     * @param resultReceiver For receiving a result message when task is finished. Can be null if you don't want to receive events.
     * @throws IllegalArgumentException
     */
    public MSTwitter(Activity activity, String consumerKey, String consumerSecret, MSTwitterResultReceiver resultReceiver) throws IllegalArgumentException {
        if (activity == null) {
            IllegalArgumentException e = new IllegalArgumentException("Context required. Cannot be null.");
            throw e;
        }
        if (consumerKey == null) {
            IllegalArgumentException e = new IllegalArgumentException("Consumer Key required. Cannot be null.");
            throw e;
        }
        if (consumerSecret == null) {
            IllegalArgumentException e = new IllegalArgumentException("Consumer Secret required. Cannot be null.");
            throw e;
        }

        // save passed in vars to module level vars
        mCallingActivity = activity;
        smConsumerKey = consumerKey;
        smConsumerSecret = consumerSecret;
        mResultReceiver = resultReceiver;

        // setup a broadcast receiver to receive update events from the twitter upload process
        clearPriorBroadcast(mCallingActivity);
        IntentFilter filter = new IntentFilter();
        filter.addAction(MSTwitter.INTENT_BROADCAST_MSTWITTER);
        mCallingActivity.registerReceiver(new MSTwitterSerciceBroadcastReceiver(), filter);

        // init object module variables;
        mDoingATweet = false;
        mText = null;
        mImagePath = null;
    }

    /**
     * Kicks off a tweet by first trying to get authorized then sending the tweet itself
     * If not authorized will first call a service to get the oAuthAccesstoken from twitter.com
     * 	then will display the twitter.com authorization page on the gui thread, finally will
     *  communicate back to twitter.com to create the authorization token completing the
     *  authorization process. Whew!
     * If is authorized or after authorization then will call a service to send the tweet.
     * Authorized or not a result event will fire when the process is finished
     * 	in the resultReceiver passed in on MSTwitter object creation unless it is null.
     * @param text Text to tweet
     * @param imagePath File path to an image to attach to the tweet.
     */
    public void startTweet(String text, String imagePath) {
        // only do one tweet at time.
        if (mDoingATweet) {
            return;
        }
        mDoingATweet = true;

        // check if there is not data to send
        if (text == null && imagePath == null) {
            // send message back to receiver
            if (mResultReceiver != null) {
                mResultReceiver.onRecieve(MSTWEET_STATUS_FINSIHED_FAILED, "No data to send.");
            }
        }
        mText = text;
        mImagePath = imagePath;

        // first see if we are authorized to send
        if (MSTwitter.authorized(mCallingActivity)) {
            // this event will terminate in UploadBroadcastReceiver below
            kickOffTweet();
        } else {
            startAuthorization();
        }
    }

    /**
     * Starts tweet by starting MSTwitterService which sends data to twitter.com.
     * Then displays a user message indicating the tweet has started.
     * When MSTwitterService is finished a broadcast event is sent and caught in
     * MSTwitterSerciceBroadcastReceiver().
     */
    private void kickOffTweet() {
        // send message back to receiver
        if (mResultReceiver != null) {
            mResultReceiver.onRecieve(MSTWEET_STATUS_STARTING, "");
        }
        Intent svc = new Intent(mCallingActivity, MSTwitterService.class);
        svc.putExtra(MSTwitterService.MST_KEY_SERVICE_TASK, MSTwitterService.MST_SERVICE_TASK_SENDTWEET);
        svc.putExtra(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
        svc.putExtra(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);
        mCallingActivity.startService(svc);
    }

    /**
     * Authorization takes place in three parts:
     * Part 1 starts a MSTwitterService to get the RequestToken from twitter.com.
     * 	when finished a broadcast event is sent and caught in MSTwitterSerciceBroadcastReceiver().
     * 	The RequestToken is then used to make an authorization url that is
     * 	sent to the second Part
     * Part 2 opens a new activity (MSTwitterAuthorizer) on the calling activity's gui
     * 	thread that displays a full screen webView with the URL obtained in part one.
     * 	After this webview is closed an event will get caught in onActivityResult() of
     * 	the calling activity which needs to call onCallingActivityResult() so MSTwitter
     * 	can catch this event.
     * Part 3 starts MSTWitterSercice again with parameters instructing it to communicate
     * 	with twitter.com to get the access token and access secret strings which are then
     * 	saved to disk using SharedPreferences interface. When finished a broadcast event
     * 	is sent and caught in MSTwitterSerciceBroadcastReceiver().
     * At this point the app is authorized and the tweet is started in kickOffTweet()
     */
    private void startAuthorization() {
        // send message back to receiver
        if (mResultReceiver != null) {
            mResultReceiver.onRecieve(MSTWEET_STATUS_AUTHORIZING, "");
        }

        // start part 1, get the authorization url
        Intent svc = new Intent(mCallingActivity, MSTwitterService.class);
        svc.putExtra(MSTwitterService.MST_KEY_SERVICE_TASK, MSTwitterService.MST_SERVICE_TASK_GET_AUTH_URL);
        svc.putExtra(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
        svc.putExtra(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);
        mCallingActivity.startService(svc);
    }

    /**
     * Routes events received broadcast by MSTwitterSercvice object
     * @param extras
     */
    private void processRecievedEvent(Bundle extras) {
        if (extras == null) {
            return;
        }
        // exit if no service task
        if (!extras.containsKey(MSTwitterService.MST_KEY_SERVICE_TASK)) {
            return;
        }

        parseTweetTextAndPath(extras);

        int task = extras.getInt(MSTwitterService.MST_KEY_SERVICE_TASK);
        switch (task) {
            case MSTwitterService.MST_SERVICE_TASK_GET_AUTH_URL:
                processGetAuthURLResult(extras);
                break;
            case MSTwitterService.MST_SERVICE_TASK_MAKE_TOKEN:
                processMakeTokenResult(extras);
                break;
            case MSTwitterService.MST_SERVICE_TASK_SENDTWEET:
                processSendTweetResult(extras);
                break;
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
     * End of Part 1 in the authorization process (see startAuthorization() comment.)
     * Starts Part 2, displaying twitter.com authorization web page.
     * @param extras Bundle containing the authorization URL
     */
    private void processGetAuthURLResult(Bundle extras) {
        if (extras.containsKey(MSTwitterService.MST_KEY_AUTHURL_RESULT)) {
            int authResult = extras.getInt(MSTwitterService.MST_KEY_AUTHURL_RESULT);

            // see if auth was successful
            if (authResult == MST_RESULT_SUCCESSFUL) {
                // worked so get the url
                if (extras.containsKey(MSTwitterService.MST_KEY_AUTHURL_RESULT_URL)) {
                    String url = extras.getString(MSTwitterService.MST_KEY_AUTHURL_RESULT_URL);

                    // start the authorizer activity (Part 2 of authorization)
                    Intent intent = new Intent(mCallingActivity, MSTwitterAuthorizer.class);
                    intent.putExtra(MSTwitterAuthorizer.MST_KEY_AUTH_URL, url);
                    intent.putExtra(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
                    intent.putExtra(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);
                    mCallingActivity.startActivityForResult(intent, REQUEST_TWITTER_AUTH);

                } else {
                    // should never happen
                    finishTweet(false, "No url returned from auth token.");
                }
            } else {
                String resultDesc = getResultDescription(authResult, mCallingActivity);
                finishTweet(false, resultDesc);
            }
        }
    }

    /**
     * End of part 2 in the authorization process (see startAuthorization() comment.)
     * Needs to be called from the calling activity's onActivityResult() function
     * for authorization to complete.
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void onCallingActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TWITTER_AUTH) {
            if (resultCode == MST_RESULT_SUCCESSFUL) {
                processAuthorizerResponse(data);
            } else {
                // something went wrong, finish the tweet attempt
                String resultDesc = getResultDescription(resultCode, mCallingActivity);
                finishTweet(false, resultDesc);
            }
        }
    }

    /**
     * End of part 2 in the authorization process (see startAuthorization() comment.)
     * Start part 3 of the authorization process, get the access token.
     * @param data Intent containing the oauth verifier string
     */
    private void processAuthorizerResponse(Intent data) {
        // get the oAuth string used to make the access token parts
        Bundle extras = data.getExtras();
        String oAuthVerifier = extras.getString(MSTwitterAuthorizer.MST_KEY_AUTH_OAUTH_VERIFIER);

        parseTweetTextAndPath(extras);

        if (oAuthVerifier != null) {
            // part 3 make the authorization token
            Intent svc = new Intent(mCallingActivity, MSTwitterService.class);
            svc.putExtra(MSTwitterService.MST_KEY_SERVICE_TASK, MSTwitterService.MST_SERVICE_TASK_MAKE_TOKEN);
            svc.putExtra(MSTwitterService.MST_KEY_AUTH_OAUTH_VERIFIER, oAuthVerifier);
            svc.putExtra(MSTwitterService.MST_KEY_TWEET_TEXT, mText);
            svc.putExtra(MSTwitterService.MST_KEY_TWEET_IMAGE_PATH, mImagePath);
            mCallingActivity.startService(svc);

        } else {
            finishTweet(false, "No oauth token returned from Twitter authorizer");
        }
    }

    /**
     * End of part 3 in the authorization process (see startAuthorization() comment.)
     * If authorized then kick off the tweet.
     * @param extras
     */
    private void processMakeTokenResult(Bundle extras) {
        if (extras.containsKey(MSTwitterService.MST_KEY_MAKE_TOKEN_RESULT)) {
            int makeTokenResult = extras.getInt(MSTwitterService.MST_KEY_MAKE_TOKEN_RESULT);

            // see if auth was successful
            if (makeTokenResult == MST_RESULT_SUCCESSFUL) {
                // worked so now we can start the tweet
                kickOffTweet();
            } else {
                String resultDesc = getResultDescription(makeTokenResult, mCallingActivity);
                finishTweet(false, resultDesc);
            }
        }
    }

    /**
     * Process result of sending tweet by assembling data and calling finishTweet()
     * @param extras Bundle containing tweet result and error description
     */
    private void processSendTweetResult(Bundle extras) {
        boolean tweetResult = false;
        String tweetResultDesc = "";

        // first check to see if we got a result
        if (extras.containsKey(MSTwitterService.MST_KEY_SENDTWEET_RESULT)){

            // get the data
            int tweetResultCode = extras.getInt(MSTwitterService.MST_KEY_SENDTWEET_RESULT);
            if (tweetResultCode == MST_RESULT_SUCCESSFUL) {
                tweetResult = true;
            } else {
                tweetResultDesc = getResultDescription(tweetResultCode, mCallingActivity);
            }
        }
        finishTweet(tweetResult, tweetResultDesc);
    }

    public static void clearCredentials(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PERF_FILENAME, Context.MODE_PRIVATE);
        final Editor edit = prefs.edit();
        edit.remove(PREF_ACCESS_TOKEN);
        edit.remove(PREF_ACCESS_TOKEN_SECRET);
        edit.commit();
    }
    /**
     * Called when tweet if finished. Sends result to receiver if one is available
     * @param tweetResult True if tweet was successful
     * @param tweetResultDesc Description of what went wrong if tweet was unsuccessful
     */
    private void finishTweet(boolean tweetResult, String tweetResultDesc) {
        int tweetLifeCycleEvent = MSTWEET_STATUS_FINSIHED_SUCCCESS;	// start with optimism
        if (!tweetResult) {
            tweetLifeCycleEvent = MSTWEET_STATUS_FINSIHED_FAILED;
        }

        // send back to receiver
        if (mResultReceiver != null) {
            mResultReceiver.onRecieve(tweetLifeCycleEvent, tweetResultDesc);
        }

        // indicate that tweet is finished.
        mDoingATweet = false;
    }
}
