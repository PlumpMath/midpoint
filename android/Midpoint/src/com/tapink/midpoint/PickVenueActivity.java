package com.tapink.midpoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;
import com.tapink.midpoint.calendar.Event;
import com.tapink.midpoint.map.MidpointOverlay;
import com.tapink.midpoint.map.TheirOverlay;
import com.tapink.midpoint.map.Venue;
import com.tapink.midpoint.map.VenueItem;
import com.tapink.midpoint.map.VenueOverlay;
import com.tapink.midpoint.util.DummyDataHelper;
import com.tapink.midpoint.util.GeneralConstants;
import com.tapink.midpoint.util.GeoHelper;
import com.tapink.midpoint.util.TextHelper;

public class PickVenueActivity extends MapActivity implements VenueOverlay.Delegate {

  private static final String TAG = "PickVenueActivity";

  private Context mContext = this;
  private ListView mListView;

  private MapView mMapView;
  private VenueOverlay mVenueOverlay;
  private MidpointOverlay mMidpointOverlay;
  private TheirOverlay mTheirOverlay;
  private MyLocationOverlay me;

  private VenueAdapter mAdapter;
  private Button mButton;

  // Model
  private Event mEvent;
  private Location mLastLocation;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.pick_venue);

    mButton = (Button) findViewById(R.id.button);
    mButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        toggleViews();
      }
    });

    mListView = (ListView) findViewById(R.id.list);
    mListView.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position,
                              long id) {
        Venue venue = (Venue) mAdapter.getItem(position);
        navigateToConfirmWithVenue(venue);
      }
    });

    mMapView = (MapView) findViewById(R.id.mapview);
    mMapView.setBuiltInZoomControls(true);

    me = new MyLocationOverlay(this, mMapView);
    mMapView.getOverlays().add(me);

    Drawable pin = this.getResources().getDrawable(R.drawable.marker);
    mVenueOverlay = new VenueOverlay(pin, mContext);
    mVenueOverlay.setDelegate(this);
    mMapView.getOverlays().add(mVenueOverlay);

    Intent i = getIntent();
    mEvent                 = i.getParcelableExtra("event");
    mLastLocation          = i.getParcelableExtra("my_location");
    Location midpoint      = i.getParcelableExtra("midpoint");
    Location theirLocation = i.getParcelableExtra("their_location");
    String address         = i.getStringExtra("address");

    Log.v(TAG, "Event: " + mEvent);
    Log.v(TAG, "Midpoint: " + midpoint);
    Log.v(TAG, "Address: " + address);

    if (theirLocation != null) {
      Drawable theirMarker = this.getResources().getDrawable(R.drawable.marker_inverse);
      mTheirOverlay = new TheirOverlay(theirMarker, mContext);
      mMapView.getOverlays().add(mTheirOverlay);
      mTheirOverlay.addItem(
          new OverlayItem(
              GeoHelper.locationToGeoPoint(theirLocation),
              "Their Location",
              "Your friend is here."
              )
          );
    }

    if (midpoint != null) {
      Drawable midpointMarker = this.getResources().getDrawable(R.drawable.marker_grey);
      mMidpointOverlay = new MidpointOverlay(midpointMarker, mContext);
      mMapView.getOverlays().add(mMidpointOverlay);

      mMidpointOverlay.addItem(
          new OverlayItem(
              GeoHelper.locationToGeoPoint(midpoint),
              "Midpoint",
              "Halfway point"
              )
          );

      // Make a query using the location
    } else if (address != null) {
      // Make a query using the address

    } else {
      throw new IllegalStateException("Need to have either a location or an address!");
    }

    if (GeneralConstants.OFFLINE_MODE) {
      populateSampleData();
      //populateMapFromListAdapter();
      //setupMap();
    } else {
      // Fetch live data
      populateRealData(
          midpoint,
          "cafe"
          );
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    //me.enableCompass();
    me.enableMyLocation();

    Location pos = me.getLastFix();
    if (pos != null) {
      Log.v(TAG, "Pos found: " + pos);
      //mMapView.getController().setCenter(
      //    pos
      //);
    }
    GeoPoint loc = me.getMyLocation();
    if (loc != null) {
      mMapView.getController().setCenter(
          loc
      );
    }
  }

  @Override
  public void onPause() {
    super.onPause();

    //me.disableCompass();
    me.disableMyLocation();
  }

  ////////////////////////////////////////
  // MapActivity
  ////////////////////////////////////////

  @Override
  protected boolean isRouteDisplayed() {
    return false;
  }

  ////////////////////////////////////////
  // VenueOverlay.Delegate
  ////////////////////////////////////////

  @Override
  public void venueOverlayTappedItem(final VenueItem item) {
    Log.v(TAG, String.format("venueOverlayTappedItem(%s)", item.toString()));

    AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
    dialog.setTitle(item.getTitle());
    dialog.setMessage(item.getSnippet());

    dialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {

      @Override
      public void onClick(DialogInterface arg0, int arg1) {
        // No need to do anything. Just dismiss the view.
      }

    });

    dialog.setPositiveButton(R.string.view_venue, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        // Pass in our venue data.
        Venue venue = item.getVenue();
        navigateToConfirmWithVenue(venue);
      }

    });

    dialog.show();

  }

  ////////////////////////////////////////
  // Data
  ////////////////////////////////////////

  private void populateMapFromListAdapter() {

    int count = mAdapter.getCount();

    for (int i = 0; i < count; i++) {
      //JSONObject json = (JSONObject) mAdapter.getItem(i);
      JSONObject json = ((Venue) mAdapter.getItem(i)).getJson();
      mVenueOverlay.addItem(
          VenueItem.Factory.VenueItemFromJSONObject(json)
      );
    }
  }


  private void populateSampleData() {
    DummyDataHelper helper = new DummyDataHelper(mContext);
    JSONArray venues = helper.getSampleVenues();
    loadData(venues);
  }

  private void loadData(JSONArray venues) {
    ArrayList<Venue> list = new ArrayList<Venue>();
    if (venues != null) {
      for (int i=0;i<venues.length();i++){
        try {
          Venue venue = new Venue((JSONObject) venues.get(i));
          list.add(venue);
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    }

    Venue[] venueArray = new Venue[list.size()];
    list.toArray(venueArray);
    VenueAdapter adapter = new VenueAdapter(venueArray);
    mListView.setAdapter(adapter);
    mAdapter = adapter;

    populateMapFromListAdapter();
    setupMap();
  }

  //private void populateRealData(GeoPoint point, String query) {
  private void populateRealData(Location loc, String query) {
    // TODO: Use real params

    ArrayList<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
    if (TextHelper.isEmptyString(query)) {
      query = "cafe";
    }

    params.add(new BasicNameValuePair(
        "q",
        query));

    params.add(new BasicNameValuePair(
        "lat",
        Double.toString(
            loc.getLatitude()
            )));
    params.add(new BasicNameValuePair(
        "lon",
        Double.toString(
            loc.getLongitude()
            )));
    params.add(new BasicNameValuePair(
        "client_secret",
        getResources().getText(
            R.string.hyperpublic_client_secret
            ).toString()
        ));
    params.add(new BasicNameValuePair(
        "client_id",
        getResources().getText(
            R.string.hyperpublic_client_id
            ).toString()
        ));
    String parameterString = URLEncodedUtils.format(params, "utf-8");
    String baseUrl = "https://api.hyperpublic.com/api/v1/places";
    String customString = String.format("%s?%s",
        baseUrl,
        parameterString
    );

    String queryString = "https://api.hyperpublic.com/api/v1/places?client_id=8UufhI6bCKQXKMBn7AUWO67Yq6C8RkfD0BGouTke&client_secret=zdoROY5XRN0clIWsEJyKzHedSK4irYee8jpnOXaP&location=240%20E%2086th%20st,%20new%20york,%20ny&q=cafe";

    Log.v(TAG, "queryString: " + queryString);
    Log.v(TAG, "customString: " + customString);

    HyperPublicFetchTask task = new HyperPublicFetchTask();
    task.execute(
      //queryString
      customString
    );
  }

  ////////////////////////////////////////
  // HyperPublicFetchTask
  ////////////////////////////////////////

  class HyperPublicFetchTask extends AsyncTask<String, Integer, String> {

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
    }

    @Override
    protected String doInBackground(String... urls) {
      //Log.v(TAG, "doInBackground: " + urls);
      Log.v(TAG, "doInBackground: " + urls[0]);
      return getJsonString(urls[0]);
    }

    //protected void onProgressUpdate(Integer... progress) {
    //  setProgressPercent(progress[0]);
    //}

    protected void onPostExecute(String jsonString) {
      Log.v(TAG, "onPostExecute: " + jsonString);
      JSONArray venues = null;
      try {
        venues = new JSONArray(jsonString);
      } catch (JSONException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      loadData(venues);
    }

  }

  private String getJsonString(String strUrl) {
    URL url = null;
    String jsonString = null;
    try {
      url = new URL(strUrl);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Must be a properly formed URL. Received: " + strUrl);
    }

    URLConnection conn;
    //HttpsURLConnection connection;

    // Code block for determining HTTP or https
    if (url.getProtocol().toLowerCase().equals("https")) {
        trustAllHosts();
        HttpsURLConnection https;
        try {
          https = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
          e.printStackTrace();
          return null;
        }
        https.setHostnameVerifier(DO_NOT_VERIFY);
        conn = https;
    } else {
        try {
          conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
          e.printStackTrace();
          return null;
        }
    }

    try {
      Object response = conn.getContent();
      if (response instanceof String) {
        jsonString = (String)response;
      } else if (response instanceof GZIPInputStream) {
        Log.e(TAG, "GZIPInputStream received: " + response);
        GZIPInputStream zis = (GZIPInputStream) response;
        try {
          // Reading from 'zis' gets you the uncompressed bytes...
          jsonString = processStream(zis);
        } finally {
          zis.close();
        }
      } else {
        Log.e(TAG, "Object received: " + response);
        //InputStream in = connection.getInputStream();
        //saveStreamToDisk(in);
        //bitmap = BitmapFactory.decodeStream(in);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return jsonString;
  }

  private String processStream(GZIPInputStream zis) {
    InputStreamReader reader = new InputStreamReader(zis);
    BufferedReader in = new BufferedReader(reader);

    String readed;
    try {
      while ((readed = in.readLine()) != null) {
        System.out.println(readed);
        return readed;
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  ////////////////////////////////////////
  // JSONVenueAdapter
  ////////////////////////////////////////

  private class JSONVenueAdapter extends BaseAdapter {

    private JSONArray mVenues;

    public JSONVenueAdapter(JSONArray venues) {
      this.mVenues = venues;
    }

    @Override
    public int getCount() {
      return mVenues.length();
    }

    @Override
    public Object getItem(int index) {
      try {
        return mVenues.getJSONObject(index);
      } catch (JSONException e) {
        e.printStackTrace();
      }
      return null;
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

      // Kennedy, this is where you supply an XML file to base it on.
      View view = inflater.inflate(R.layout.venue_list_item, null);
      TextView test = (TextView) view;

      JSONObject json = (JSONObject) getItem(position);
      String name = "Venue";
      try {
        name = json.getString("display_name");
      } catch (JSONException e) {
        e.printStackTrace();
      }

      test.setText(name);

      return test;
    }
  }

  private class VenueAdapter extends BaseAdapter {

    private Venue[] mVenues;

    public VenueAdapter(Venue[] venues) {
      this.mVenues = venues;
    }

    @Override
    public int getCount() {
      return mVenues.length;
    }

    @Override
    public Object getItem(int index) {
      return mVenues[index];
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

      // Kennedy, this is where you supply an XML file to base it on.
      View view = inflater.inflate(R.layout.venue_list_item, null);
      TextView test = (TextView) view;

      Venue venue = mVenues[position];
      String name = venue.getName();
      test.setText(name);
      return test;
    }
  }

  ////////////////////////////////////////
  // View Management
  ////////////////////////////////////////

  private void toggleViews() {
    if (mMapView.getVisibility() == View.VISIBLE) {
      mMapView.setVisibility(View.GONE);
      mListView.setVisibility(View.VISIBLE);
      mButton.setText(R.string.map);
    } else {
      mMapView.setVisibility(View.VISIBLE);
      mListView.setVisibility(View.GONE);
      mButton.setText(R.string.list);
    }
  }

//  // Minimum & maximum latitude so we can span it
//  // The latitude is clamped between -80 degrees and +80 degrees inclusive
//  // thus we ensure that we go beyond that number
//  private final int minLatitude = (int)(+81 * 1E6);
//  private final int maxLatitude = (int)(-81 * 1E6);
//  // Minimum & maximum longitude so we can span it
//  // The longitude is clamped between -180 degrees and +180 degrees inclusive
//  // thus we ensure that we go beyond that number
//  private final int minLongitude = (int)(+181 * 1E6);
//  private final int maxLongitude = (int)(-181 * 1E6);


  private GeoPoint getMyLocation() {
    GeoPoint current = me.getMyLocation();
    if (current != null) {
      return current;
    } else {
      return GeoHelper.locationToGeoPoint(mLastLocation);
    }
  }

  private void setupMap() {
    MapController controller = mMapView.getController();
    ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();

    GeoPoint current = getMyLocation();
    if (current != null) {
      points.add(current);
    }

    for (int i = 0; i < mMidpointOverlay.size(); i++) {
      GeoPoint point = mMidpointOverlay.getItem(i).getPoint();
      if (point != null) {
        points.add(point);
      }
    }

    for (int i = 0; i < mVenueOverlay.size(); i++) {
      GeoPoint point = mVenueOverlay.getItem(i).getPoint();
      if (point != null) {
        points.add(point);
      }
    }

    for (int i = 0; i < mTheirOverlay.size(); i++) {
      GeoPoint point = mTheirOverlay.getItem(i).getPoint();
      if (point != null) {
        points.add(point);
      }
    }

    setupMap(
        points,
        controller
        );
  }

  private void setupMap(Iterable<GeoPoint> points, MapController mapController) {
    // Minimum & maximum latitude so we can span it
    // The latitude is clamped between -80 degrees and +80 degrees inclusive
    // thus we ensure that we go beyond that number
    int minLatitude = (int)(+81 * 1E6);
    int maxLatitude = (int)(-81 * 1E6);
    // Minimum & maximum longitude so we can span it
    // The longitude is clamped between -180 degrees and +180 degrees inclusive
    // thus we ensure that we go beyond that number
    int minLongitude = (int)(+181 * 1E6);
    int maxLongitude = (int)(-181 * 1E6);

    // Holds all the picture location as Point
    //List<Point> mPoints = new ArrayList<Point>();
    for (GeoPoint point : points) {

      //You get the latitude/longitude as you want, here I take it from the db
      int latitude   = point.getLatitudeE6();
      int longitude  = point.getLongitudeE6();

      // Sometimes the longitude or latitude gathering
      // did not work so skipping the point
      // doubt anybody would be at 0 0
      if (latitude != 0 && longitude !=0)  {

        // Sets the minimum and maximum latitude so we can span and zoom
        minLatitude = (minLatitude > latitude) ? latitude : minLatitude;
        maxLatitude = (maxLatitude < latitude) ? latitude : maxLatitude;
        // Sets the minimum and maximum latitude so we can span and zoom
        minLongitude = (minLongitude > longitude) ? longitude : minLongitude;
        maxLongitude = (maxLongitude < longitude) ? longitude : maxLongitude;

        //mPoints.add(new Point(latitude, longitude));
      }
    }

    // Zoom to span from the list of points
    mapController.zoomToSpan(
        (maxLatitude - minLatitude),
        (maxLongitude - minLongitude));
    // Animate to the center cluster of points
    mapController.animateTo(new GeoPoint(
        (maxLatitude + minLatitude)/2,
        (maxLongitude + minLongitude)/2 ));

    // Add all the point to the overlay
    //mMapOverlay = new MyPhotoMapOverlay(mPoints);
    //mMapOverlayController = mMapView.createOverlayController();
    //// Add the overlay to the mapview
    //mMapOverlayController.add(mMapOverlay, true);
  }

  ////////////////////////////////////////
  // Navigation
  ////////////////////////////////////////

  private void navigateToConfirmWithVenue(Venue venue) {
    Intent i = new Intent(PickVenueActivity.this, ConfirmVenueActivity.class);

    JSONObject json = venue.getJson();
    if (json == null) {
      // Shit hit the fan!
      Log.e(TAG, "Illegal venue!");
      throw new IllegalStateException("Venue didn't have any underlying JSON object!");
    } else {
      i.putExtra("event", mEvent);
      i.putExtra("venue", venue);

      startActivity(i);
    }
  }


    // always verify the host - dont check for certificate
    final static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    };


    /**
     * Trust every server - dont check for any certificate
     */
    private static void trustAllHosts() {
      // Create a trust manager that does not validate certificate chains
      TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
          return new java.security.cert.X509Certificate[] {};
        }

        @Override
        public void checkClientTrusted(
            java.security.cert.X509Certificate[] chain, String authType)
            throws java.security.cert.CertificateException {
          // TODO Auto-generated method stub

        }

        @Override
        public void checkServerTrusted(
            java.security.cert.X509Certificate[] chain, String authType)
            throws java.security.cert.CertificateException {
          // TODO Auto-generated method stub

        }
      } };

      // Install the all-trusting trust manager
      try {
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection
            .setDefaultSSLSocketFactory(sc.getSocketFactory());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

}
