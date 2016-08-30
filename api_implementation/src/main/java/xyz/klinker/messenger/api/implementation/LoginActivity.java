/*
 * Copyright (C) 2016 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.api.implementation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;

import xyz.klinker.messenger.api.entity.DeviceBody;
import xyz.klinker.messenger.api.entity.LoginResponse;
import xyz.klinker.messenger.api.entity.SignupResponse;
import xyz.klinker.messenger.encryption.KeyUtils;

/**
 * Activity for logging a user in using the API
 */
public class LoginActivity extends AppCompatActivity {

    public static final int RESULT_START_NETWORK_SYNC = 32;
    public static final int RESULT_START_DEVICE_SYNC = 33;

    private boolean isInitial = true;

    private FloatingActionButton fab;
    private EditText email;
    private EditText password;
    private EditText passwordConfirmation;
    private EditText name;
    private EditText phoneNumber;
    private ProgressDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.api_activity_login);
        setUpInitialLayout();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                circularRevealIn();
            }
        }, 100);
    }

    private void setUpInitialLayout() {
        Button login = (Button) findViewById(R.id.login);
        Button signup = (Button) findViewById(R.id.signup);
        View signupFailed = findViewById(R.id.signup_failed);
        Button skip = (Button) findViewById(R.id.skip);

        if (getPhoneNumber() == null) {
            signup.setEnabled(false);
            signupFailed.setVisibility(View.VISIBLE);
            findViewById(R.id.skip_holder).setVisibility(View.GONE);
        }

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                login();
            }
        });

        signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signup();
            }
        });

        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void login() {
        slideLoginIn();

        fab = (FloatingActionButton) findViewById(R.id.login_fab);
        email = (EditText) findViewById(R.id.login_email);
        password = (EditText) findViewById(R.id.login_password);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performLogin();
            }
        });

        password.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                boolean handled = false;

                if ((keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                        keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) ||
                        actionId == EditorInfo.IME_ACTION_DONE) {
                    fab.performClick();
                }

                return handled;
            }
        });

        fab.hide();
        attachLoginTextWatcher(email);
        attachLoginTextWatcher(password);
    }

    private void signup() {
        slideSignUpIn();

        fab = (FloatingActionButton) findViewById(R.id.signup_fab);
        email = (EditText) findViewById(R.id.signup_email);
        password = (EditText) findViewById(R.id.signup_password);
        passwordConfirmation = (EditText) findViewById(R.id.signup_password_confirmation);
        name = (EditText) findViewById(R.id.signup_name);
        phoneNumber = (EditText) findViewById(R.id.signup_phone_number);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performSignup();
            }
        });

        fab.hide();
        attachSignupTextWatcher(email);
        attachSignupTextWatcher(password);
        attachSignupTextWatcher(passwordConfirmation);
        attachSignupTextWatcher(name);
        attachSignupTextWatcher(phoneNumber);

        name.setText(getName());

        String number = getPhoneNumber();
        phoneNumber.setText(number);
        if (number == null || number.trim().length() == 0) {
            phoneNumber.setEnabled(true);
        }
    }

    private void performLogin() {
        dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.api_connecting));
        dialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                ApiUtils utils = new ApiUtils();
                final LoginResponse response = utils.login(email.getText().toString(),
                        password.getText().toString());

                if (response == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            setResult(RESULT_CANCELED);
                            Toast.makeText(getApplicationContext(), R.string.api_error,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    KeyUtils keyUtils = new KeyUtils();
                    SharedPreferences sharedPrefs = PreferenceManager
                            .getDefaultSharedPreferences(getApplicationContext());
                    sharedPrefs.edit()
                            .putString("my_name", response.name)
                            .putString("my_phone_number", response.phoneNumber)
                            .putString("account_id", response.accountId)
                            .putString("salt", response.salt1)
                            .putString("passhash", keyUtils.hashPassword(
                                    password.getText().toString(), response.salt2))
                            .apply();

                    addDevice(utils, response.accountId, getPhoneNumber() != null, false);
                }
            }
        }).start();
    }

    private void performSignup() {
        dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.api_connecting));
        dialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                ApiUtils utils = new ApiUtils();
                final SignupResponse response = utils.signup(email.getText().toString(),
                        password.getText().toString(), name.getText().toString(),
                        phoneNumber.getText().toString());

                if (response == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            setResult(RESULT_CANCELED);
                            Toast.makeText(getApplicationContext(), R.string.api_error,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    KeyUtils keyUtils = new KeyUtils();
                    SharedPreferences sharedPrefs = PreferenceManager
                            .getDefaultSharedPreferences(getApplicationContext());
                    sharedPrefs.edit()
                            .putString("my_name", name.getText().toString())
                            .putString("my_phone_number", phoneNumber.getText().toString())
                            .putString("account_id", response.accountId)
                            .putString("salt", response.salt1)
                            .putString("passhash", keyUtils.hashPassword(
                                    password.getText().toString(), response.salt2))
                            .apply();

                    addDevice(utils, response.accountId, true, true);
                }
            }
        }).start();
    }

    private void addDevice(final ApiUtils utils, final String accountId, final boolean primary,
                           final boolean deviceSync) {
        Integer deviceId = utils.registerDevice(accountId,
                Build.MANUFACTURER + ", " + Build.MODEL, Build.MODEL,
                primary, getFirebaseId());

        if (deviceId != null) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString("device_id", Integer.toString(deviceId))
                    .putBoolean("primary", primary)
                    .apply();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog.dismiss();
                    setResult(deviceSync ? RESULT_START_DEVICE_SYNC : RESULT_START_NETWORK_SYNC);
                    close();
                }
            });
        } else {
            DeviceBody[] devices = utils.getDevices(accountId);
            if (devices == null) {
                failAddDevice();
            } else {
                int primaryLocation = -1;
                for (int i = 0; i < devices.length; i++) {
                    if (devices[i].primary) {
                        primaryLocation = i;
                        break;
                    }
                }

                if (primaryLocation == -1) {
                    failAddDevice();
                    return;
                }

                final DeviceBody device = devices[primaryLocation];

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String message = getString(R.string.api_add_second_primary_device,
                                device.name);

                        new AlertDialog.Builder(LoginActivity.this)
                                .setMessage(message)
                                .setPositiveButton(R.string.api_yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                utils.removeDevice(accountId, device.id);
                                                addDevice(utils, accountId, true, false);
                                            }
                                        }).start();
                                    }
                                })
                                .setNegativeButton(R.string.api_no, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addDevice(utils, accountId, false, false);
                                            }
                                        }).start();
                                    }
                                })
                                .show();
                    }
                });
            }
        }
    }

    private void failAddDevice() {
        Log.v("LoginActivity", "failed and closing");
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString("device_id", null)
                .putBoolean("primary", false)
                .apply();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), R.string.api_device_error,
                        Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                close();
            }
        });
    }

    private void attachLoginTextWatcher(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isFilled(email) && isFilled(password) && isValidEmail(email.getText())) {
                    fab.show();
                } else {
                    fab.hide();
                }
            }
        });
    }

    private void attachSignupTextWatcher(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isFilled(email) && isFilled(password) && isFilled(passwordConfirmation) &&
                        isFilled(name) && isFilled(phoneNumber) && isValidEmail(email.getText()) &&
                        passwordConfirmation.getText().toString().equals(password.getText().toString())) {
                    fab.show();
                } else {
                    fab.hide();
                }
            }
        });
    }

    private boolean isFilled(EditText editText) {
        return editText.getText() != null && editText.getText().length() != 0;
    }

    private void circularRevealIn() {
        View view = findViewById(R.id.initial_layout);
        view.setVisibility(View.VISIBLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int cx = view.getWidth() / 2;
            int cy = view.getHeight() / 2;
            float finalRadius = (float) Math.hypot(cx, cy);
            ViewAnimationUtils.createCircularReveal(view, cx, cy, 0, finalRadius).start();
        } else {
            view.setAlpha(0f);
            view.animate().alpha(1f).start();
        }
    }

    private void circularRevealOut() {
        final View view = findVisibleHolder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int cx = view.getWidth() / 2;
            int cy = view.getHeight() / 2;
            float initialRadius = (float) Math.hypot(cx, cy);
            Animator anim =
                    ViewAnimationUtils.createCircularReveal(view, cx, cy, initialRadius, 0);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    view.setVisibility(View.INVISIBLE);
                    close();
                }
            });

            anim.start();
        } else {
            view.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    close();
                }
            }).start();
        }
    }

    private void slideLoginIn() {
        slideIn(findViewById(R.id.login_layout));
    }

    private void slideSignUpIn() {
        slideIn(findViewById(R.id.signup_layout));
    }

    private void slideIn(View view) {
        isInitial = false;
        final View initial = findViewById(R.id.initial_layout);

        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationX(view.getWidth());
        view.animate()
                .alpha(1f)
                .translationX(0)
                .setListener(null)
                .start();

        initial.animate()
                .alpha(0f)
                .translationX(-1 * initial.getWidth())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        initial.setVisibility(View.INVISIBLE);
                        initial.setTranslationX(0);
                    }
                }).start();
    }

    private void slideOut() {
        isInitial = true;
        final View visible = findVisibleHolder();
        View initial = findViewById(R.id.initial_layout);

        visible.animate()
                .alpha(0f)
                .translationX(visible.getWidth())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        visible.setVisibility(View.INVISIBLE);
                        visible.setTranslationX(0);
                    }
                }).start();

        initial.setVisibility(View.VISIBLE);
        initial.setAlpha(0f);
        initial.setTranslationX(-1 * initial.getWidth());
        initial.animate()
                .alpha(1f)
                .translationX(0)
                .setListener(null)
                .start();
    }

    private View findVisibleHolder() {
        View initial = findViewById(R.id.initial_layout);
        View login = findViewById(R.id.login_layout);
        View signup = findViewById(R.id.signup_layout);

        if (initial.getVisibility() != View.INVISIBLE) {
            return initial;
        } else if (login.getVisibility() != View.INVISIBLE) {
            return login;
        } else {
            return signup;
        }
    }

    @Override
    public void onBackPressed() {
        if (isInitial) {
            circularRevealOut();
        } else {
            slideOut();
        }
    }

    private void close() {
        finish();
        overridePendingTransition(0, 0);
    }

    private String getName() {
        Cursor cursor = getContentResolver()
                .query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst();
            String name = cursor.getString(cursor.getColumnIndex("display_name"));
            cursor.close();
            return name;
        }

        return null;
    }

    private String getPhoneNumber() {
        TelephonyManager telephonyManager = (TelephonyManager)
                getSystemService(Context.TELEPHONY_SERVICE);
        return PhoneNumberUtils.stripSeparators(telephonyManager.getLine1Number());
    }

    private String getFirebaseId() {
        return FirebaseInstanceId.getInstance().getToken();
    }

    private boolean isValidEmail(CharSequence target) {
        return !TextUtils.isEmpty(target) &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

}
