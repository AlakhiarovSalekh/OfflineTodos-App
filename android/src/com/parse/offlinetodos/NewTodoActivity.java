package com.parse.offlinetodos;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.parse.GetCallback;
import com.parse.ParseException;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

public class NewTodoActivity extends Activity {

	private Button saveButton;
	private Button deleteButton;
	private EditText todoText;
	private Todo todo;
	private String todoId = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_todo);

		// Fetch the todoId from the Extra data
		if (getIntent().hasExtra("ID")) {
			todoId = getIntent().getExtras().getString("ID");
		}

		todoText = (EditText) findViewById(R.id.todo_text);
		saveButton = (Button) findViewById(R.id.saveButton);
		deleteButton = (Button) findViewById(R.id.deleteButton);

		if (todoId == null) {
			todo = new Todo();
			todo.setUuidString();
		} else {
			ParseQuery<Todo> query = Todo.getQuery();
			query.fromLocalDatastore();
			query.whereEqualTo("uuid", todoId);
			query.getFirstInBackground(new GetCallback<Todo>() {

				@Override
				public void done(Todo object, ParseException e) {
					if (!isFinishing()) {
						todo = object;
						todoText.setText(todo.getTitle());
						deleteButton.setVisibility(View.VISIBLE);
					}
				}

			});

		}

		saveButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				todo.setTitle(todoText.getText().toString());
				todo.setDraft(true);
				todo.setAuthor(ParseUser.getCurrentUser());
				todo.pinInBackground(TodoListApplication.TODO_GROUP_NAME,
						new SaveCallback() {

							@Override
							public void done(ParseException e) {
								if (isFinishing()) {
									return;
								}
								if (e == null) {
									setResult(Activity.RESULT_OK);
									finish();
								} else {
									Toast.makeText(getApplicationContext(),
											"Error saving: " + e.getMessage(),
											Toast.LENGTH_LONG).show();
								}
							}

						});
			}

		});

		deleteButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// The todo will be deleted eventually but will
				// immediately be excluded from query results.
				todo.deleteEventually();
				setResult(Activity.RESULT_OK);
				finish();
			}

		});

	}

}
