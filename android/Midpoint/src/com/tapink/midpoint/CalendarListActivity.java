package com.tapink.midpoint;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class CalendarListActivity extends Activity {
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    final Button actionConfirmButton = (Button) findViewById(R.id.button);
    actionConfirmButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {        
        Intent i = new Intent(CalendarListActivity.this, LocationActivity.class);
        startActivity(i);
      }
    });

  }

  private void launchCalendar() {
    long eventStartInMillis = System.currentTimeMillis();
    long eventEndInMillis = eventStartInMillis + 60 * 60 * 1000;

    Intent intent = new Intent(Intent.ACTION_EDIT);  
    intent.setType("vnd.android.cursor.item/event");
    intent.putExtra("title", "Some title");
    intent.putExtra("description", "Some description");
    intent.putExtra("beginTime", eventStartInMillis);
    intent.putExtra("endTime", eventEndInMillis);
    startActivity(intent);
  }

}
