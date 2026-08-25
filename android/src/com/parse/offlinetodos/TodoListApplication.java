package com.parse.offlinetodos;

import android.app.Application;

import com.parse.Parse;
import com.parse.ParseACL;
import com.parse.ParseObject;
import com.parse.ParseUser;

public class TodoListApplication extends Application {
	
	public static final String TODO_GROUP_NAME = "ALL_TODOS";
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		// add Todo subclass
		ParseObject.registerSubclass(Todo.class);
		
		// enable the Local Datastore
		Parse.enableLocalDatastore(getApplicationContext());
		Parse.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY");
		ParseUser.enableAutomaticUser();
		ParseACL defaultACL = new ParseACL();
		ParseACL.setDefaultACL(defaultACL, true);	
	}
	
	

}
