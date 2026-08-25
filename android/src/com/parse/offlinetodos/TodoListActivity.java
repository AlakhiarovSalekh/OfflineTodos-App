package com.parse.offlinetodos;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.FindCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;
import com.parse.ParseUser;
import com.parse.SaveCallback;
import com.parse.ui.ParseLoginBuilder;

public class TodoListActivity extends Activity {

	private static final int LOGIN_ACTIVITY_CODE = 100;
	private static final int EDIT_ACTIVITY_CODE = 200;

	// Adapter for the Todos Parse Query
	private ParseQueryAdapter<Todo> todoListAdapter;

	private LayoutInflater inflater;

	// For showing empty and non-empty todo views
	private ListView todoListView;
	private LinearLayout noTodosView;

	private TextView loggedInInfoView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_todo_list);

		// Set up the views
		todoListView = (ListView) findViewById(R.id.todo_list_view);
		noTodosView = (LinearLayout) findViewById(R.id.no_todos_view);
		todoListView.setEmptyView(noTodosView);
		loggedInInfoView = (TextView) findViewById(R.id.loggedin_info);

		// Set up the Parse query to use in the adapter
		ParseQueryAdapter.QueryFactory<Todo> factory = new ParseQueryAdapter.QueryFactory<Todo>() {
			public ParseQuery<Todo> create() {
				ParseQuery<Todo> query = Todo.getQuery();
				query.orderByDescending("createdAt");
				query.fromLocalDatastore();
				return query;
			}
		};
		// Set up the adapter
		inflater = (LayoutInflater) this
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		todoListAdapter = new ToDoListAdapter(this, factory);

		// Attach the query adapter to the view
		ListView todoListView = (ListView) findViewById(R.id.todo_list_view);
		todoListView.setAdapter(todoListAdapter);

		todoListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Todo todo = todoListAdapter.getItem(position);
				openEditView(todo);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Check if we have a real user
		if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
			// Sync data to Parse
			syncTodosToParse();
			// Update the logged in label info
			updateLoggedInInfo();
		}
	}

	private void updateLoggedInInfo() {
		if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
			ParseUser currentUser = ParseUser.getCurrentUser();
			loggedInInfoView.setText(getString(R.string.logged_in,
					currentUser.getString("name")));
		} else {
			loggedInInfoView.setText(getString(R.string.not_logged_in));
		}
	}

	private void openEditView(Todo todo) {
		Intent i = new Intent(this, NewTodoActivity.class);
		i.putExtra("ID", todo.getUuidString());
		startActivityForResult(i, EDIT_ACTIVITY_CODE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		// An OK result means the pinned dataset changed or
		// log in was successful
		if (resultCode == RESULT_OK) {
			if (requestCode == EDIT_ACTIVITY_CODE) {
				// Coming back from the edit view, update the view
				todoListAdapter.loadObjects();
			} else if (requestCode == LOGIN_ACTIVITY_CODE) {
				// If the user is new, sync data to Parse,
				// else get the current list from Parse
				if (ParseUser.getCurrentUser().isNew()) {
					syncTodosToParse();
				} else {
					loadFromParse();
				}
			}

		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.todo_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_new) {
			// Make sure there's a valid user, anonymous
			// or regular
			if (ParseUser.getCurrentUser() != null) {
				startActivityForResult(new Intent(this, NewTodoActivity.class),
						EDIT_ACTIVITY_CODE);
			}
		}

		if (item.getItemId() == R.id.action_sync) {
			syncTodosToParse();
		}

		if (item.getItemId() == R.id.action_logout) {
			// Log out the current user
			ParseUser.logOut();
			// Create a new anonymous user
			ParseAnonymousUtils.logIn(null);
			// Update the logged in label info
			updateLoggedInInfo();
			// Clear the view
			todoListAdapter.clear();
			// Unpin all the current objects
			ParseObject
					.unpinAllInBackground(TodoListApplication.TODO_GROUP_NAME);
		}

		if (item.getItemId() == R.id.action_login) {
			ParseLoginBuilder builder = new ParseLoginBuilder(this);
			startActivityForResult(builder.build(), LOGIN_ACTIVITY_CODE);
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		boolean realUser = !ParseAnonymousUtils.isLinked(ParseUser
				.getCurrentUser());
		menu.findItem(R.id.action_login).setVisible(!realUser);
		menu.findItem(R.id.action_logout).setVisible(realUser);
		return true;
	}

	private void syncTodosToParse() {
		// We could use saveEventually here, but we want to have some UI
		// around whether or not the draft has been saved to Parse
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if ((ni != null) && (ni.isConnected())) {
			if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
				// If we have a network connection and a current logged in user,
				// sync the
				// todos

				// In this app, local changes should overwrite content on the
				// server.

				ParseQuery<Todo> query = Todo.getQuery();
				query.fromPin(TodoListApplication.TODO_GROUP_NAME);
				query.whereEqualTo("isDraft", true);
				query.findInBackground(new FindCallback<Todo>() {
					public void done(List<Todo> todos, ParseException e) {
						if (e == null) {
							for (final Todo todo : todos) {
								// Set is draft flag to false before
								// syncing to Parse
								todo.setDraft(false);
								todo.saveInBackground(new SaveCallback() {

									@Override
									public void done(ParseException e) {
										if (e == null) {
											// Let adapter know to update view
											if (!isFinishing()) {
												todoListAdapter
														.notifyDataSetChanged();
											}
										} else {
											// Reset the is draft flag locally
											// to true
											todo.setDraft(true);
										}
									}

								});

							}
						} else {
							Log.i("TodoListActivity",
									"syncTodosToParse: Error finding pinned todos: "
											+ e.getMessage());
						}
					}
				});
			} else {
				// If we have a network connection but no logged in user, direct
				// the person to log in or sign up.
				ParseLoginBuilder builder = new ParseLoginBuilder(this);
				startActivityForResult(builder.build(), LOGIN_ACTIVITY_CODE);
			}
		} else {
			// If there is no connection, let the user know the sync didn't
			// happen
			Toast.makeText(
					getApplicationContext(),
					"Your device appears to be offline. Some todos may not have been synced to Parse.",
					Toast.LENGTH_LONG).show();
		}

	}

	private void loadFromParse() {
		ParseQuery<Todo> query = Todo.getQuery();
		query.whereEqualTo("author", ParseUser.getCurrentUser());
		query.findInBackground(new FindCallback<Todo>() {
			public void done(List<Todo> todos, ParseException e) {
				if (e == null) {
					ParseObject.pinAllInBackground((List<Todo>) todos,
							new SaveCallback() {
								public void done(ParseException e) {
									if (e == null) {
										if (!isFinishing()) {
											todoListAdapter.loadObjects();
										}
									} else {
										Log.i("TodoListActivity",
												"Error pinning todos: "
														+ e.getMessage());
									}
								}
							});
				} else {
					Log.i("TodoListActivity",
							"loadFromParse: Error finding pinned todos: "
									+ e.getMessage());
				}
			}
		});
	}

	private class ToDoListAdapter extends ParseQueryAdapter<Todo> {

		public ToDoListAdapter(Context context,
				ParseQueryAdapter.QueryFactory<Todo> queryFactory) {
			super(context, queryFactory);
		}

		@Override
		public View getItemView(Todo todo, View view, ViewGroup parent) {
			ViewHolder holder;
			if (view == null) {
				view = inflater.inflate(R.layout.list_item_todo, parent, false);
				holder = new ViewHolder();
				holder.todoTitle = (TextView) view
						.findViewById(R.id.todo_title);
				view.setTag(holder);
			} else {
				holder = (ViewHolder) view.getTag();
			}
			TextView todoTitle = holder.todoTitle;
			todoTitle.setText(todo.getTitle());
			if (todo.isDraft()) {
				todoTitle.setTypeface(null, Typeface.ITALIC);
			} else {
				todoTitle.setTypeface(null, Typeface.NORMAL);
			}
			return view;
		}
	}

	private static class ViewHolder {
		TextView todoTitle;
	}
}
