package com.example.mahabali.socketiochat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.LinearLayoutManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import android.support.v7.widget.RecyclerView;

import com.example.mahabali.Constants;
import com.example.mahabali.socketioservice.SocketEventConstants;
import com.example.mahabali.socketioservice.SocketListener;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MainFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
/**
 * A chat fragment containing messages view and input form.
 */
public class MainFragment extends Fragment implements SocketListener {

    private static final int REQUEST_LOGIN = 0;

    private static final int TYPING_TIMER_LENGTH = 600;

    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<Message> mMessages = new ArrayList<Message>();
    private MessageAdapter mAdapter;
    private boolean mTyping = false;
    private Handler mTypingHandler = new Handler();
    private String mUsername;

    public MainFragment() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAdapter = new MessageAdapter(activity, mMessages);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSocketListener.getInstance().setActiveSocketListener(this);
        // Restart Socket.io to avoid weird stuff ;-)
        AppSocketListener.getInstance().restartSocket();
        setHasOptionsMenu(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        attemptAutoLogin();
    }

    @Override
    public void onResume() {
        super.onResume();
        scrollToBottom();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onDestroy() {
        removeHandlers();
        super.onDestroy();
    }

    public void removeHandlers(){
        AppSocketListener.getInstance().off(Socket.EVENT_CONNECT_ERROR);
        AppSocketListener.getInstance().off(Socket.EVENT_CONNECT_TIMEOUT);
        AppSocketListener.getInstance().off(SocketEventConstants.newMessage);
        AppSocketListener.getInstance().off(SocketEventConstants.userJoined);
        AppSocketListener.getInstance().off(SocketEventConstants.userLeft);
        AppSocketListener.getInstance().off(SocketEventConstants.typing);
        AppSocketListener.getInstance().off(SocketEventConstants.stopTyping);
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == 0 || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });
        mInputMessageView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null == mUsername) return;
                // if (!mSocket.connected()) return;

                if (!mTyping) {
                    mTyping = true;
                    AppSocketListener.getInstance().emit("typing");
                }

                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Activity.RESULT_OK != resultCode) {
            getActivity().finish();
            return;
        }
        mUsername = data.getStringExtra("username");
        int numUsers = data.getIntExtra("numUsers", 1);
        addLog(getResources().getString(R.string.message_welcome));
        addParticipantsLog(numUsers);
    }



    private void addLog(final String message) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMessages.add(new Message.Builder(Message.TYPE_LOG)
                            .message(message).build());
                    mAdapter.notifyItemInserted(mMessages.size() - 1);
                    scrollToBottom();
                }
            });
    }

    private void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message) {
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        mMessages.add(new Message.Builder(Message.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            Message message = mMessages.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend() {
        if (null == mUsername) return;
        mTyping = false;
        String message = mInputMessageView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            mInputMessageView.requestFocus();
            return;
        }
        mInputMessageView.setText("");
        addMessage(mUsername, message);
        // perform the sending message attempt.
        AppSocketListener.getInstance().emit("new message", message);
    }

    private void attemptAutoLogin(){
        if (PreferenceStorage.shouldDoAutoLogin()){
            mUsername = PreferenceStorage.getUsername();
        }
        else{
            startSignIn();
        }
    }
    private void startSignIn() {
        mUsername = null;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void leave() {
        removeHandlers();
        mUsername = null;
        PreferenceStorage.clearUserSession();
        AppSocketListener.getInstance().signOutUser();
        startSignIn();
    }

    public void askForLogout(){
        new AlertDialog.Builder(getActivity())
                .setTitle("Logout")
                .setMessage("Do you want to logout")
                .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                        leave();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i("Failed","Failed to connect");
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                    } catch (JSONException e) {
                        return;
                    }
                    removeTyping(username);
                    addMessage(username, message);
                }
            });
        }
    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }
                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onLogin = new Emitter.Listener() {
        @Override
        public void call(Object... args) {

            JSONObject data = (JSONObject) args[0];

            int numUsers;
            try {
                numUsers = data.getInt("numUsers");
            } catch (JSONException e) {
                return;
            }
            addLog(getResources().getString(R.string.message_welcome));
            addParticipantsLog(numUsers);
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;
            mTyping = false;
            AppSocketListener.getInstance().emit(SocketEventConstants.stopTyping);
        }
    };

    @Override
    public void onSocketConnected() {
        AppSocketListener.getInstance().addOnHandler(Socket.EVENT_CONNECT_ERROR, onConnectError);
        AppSocketListener.getInstance().addOnHandler(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        AppSocketListener.getInstance().addOnHandler(SocketEventConstants.userJoined, onUserJoined);
        AppSocketListener.getInstance().addOnHandler(SocketEventConstants.userLeft, onUserLeft);
        AppSocketListener.getInstance().addOnHandler(SocketEventConstants.typing, onTyping);
        AppSocketListener.getInstance().addOnHandler(SocketEventConstants.stopTyping, onStopTyping);
        AppSocketListener.getInstance().addOnHandler(SocketEventConstants.login, onLogin);
        if (mUsername != null) {
           AppSocketListener.getInstance().emit(SocketEventConstants.addUser, mUsername);
        }
    }

    @Override
    public void onSocketDisconnected() {

    }

    @Override
    public void onNewMessageReceived(final String username, final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                removeTyping(username);
                addMessage(username, message);
            }
        });

    }
}

