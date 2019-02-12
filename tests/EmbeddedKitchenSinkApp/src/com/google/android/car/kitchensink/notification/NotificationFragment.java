package com.google.android.car.kitchensink.notification;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.util.HashMap;

/**
 * Test fragment that can send all sorts of notifications.
 */
public class NotificationFragment extends Fragment {
    private static final String IMPORTANCE_HIGH_ID = "importance_high";
    private static final String IMPORTANCE_HIGH_NO_SOUND_ID = "importance_high_no_sound";
    private static final String IMPORTANCE_DEFAULT_ID = "importance_default";
    private static final String IMPORTANCE_LOW_ID = "importance_low";
    private static final String IMPORTANCE_MIN_ID = "importance_min";
    private static final String IMPORTANCE_NONE_ID = "importance_none";
    private int mCurrentNotificationId = 0;
    private NotificationManager mManager;
    private Context mContext;
    private Handler mHandler = new Handler();
    private HashMap<Integer, Runnable> mUpdateRunnables = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getActivity();
        mManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_HIGH_ID, "Importance High", NotificationManager.IMPORTANCE_HIGH));

        NotificationChannel noSoundChannel = new NotificationChannel(
                IMPORTANCE_HIGH_NO_SOUND_ID, "No sound", NotificationManager.IMPORTANCE_HIGH);
        noSoundChannel.setSound(null, null);
        mManager.createNotificationChannel(noSoundChannel);

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_DEFAULT_ID,
                "Importance Default",
                NotificationManager.IMPORTANCE_DEFAULT));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_LOW_ID, "Importance Low", NotificationManager.IMPORTANCE_LOW));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_MIN_ID, "Importance Min", NotificationManager.IMPORTANCE_MIN));

        mManager.createNotificationChannel(new NotificationChannel(
                IMPORTANCE_NONE_ID, "Importance None", NotificationManager.IMPORTANCE_NONE));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.notification_fragment, container, false);

        initCancelAllButton(view);

        initCarCategoriesButton(view);

        initImportanceHighBotton(view);
        initImportanceDefaultButton(view);
        initImportanceLowButton(view);
        initImportanceMinButton(view);

        initOngoingButton(view);
        initMessagingStyleButton(view);
        initProgressButton(view);
        initNavigationButton(view);

        return view;
    }

    private void initCancelAllButton(View view) {
        view.findViewById(R.id.cancel_all_button).setOnClickListener(v -> {
            for (Runnable runnable : mUpdateRunnables.values()) {
                mHandler.removeCallbacks(runnable);
            }
            mUpdateRunnables.clear();
            mManager.cancelAll();
        });
    }

    private void initCarCategoriesButton(View view) {
        view.findViewById(R.id.category_car_emergency_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Car Emergency")
                    .setContentText("Shows heads-up; Shows on top of the list; Does not group")
                    .setCategory(Notification.CATEGORY_CAR_EMERGENCY)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

        view.findViewById(R.id.category_car_warning_button).setOnClickListener(v -> {

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_MIN_ID)
                    .setContentTitle("Car Warning")
                    .setContentText(
                            "Shows heads-up; Shows on top of the list but below Car Emergency; "
                                    + "Does not group")
                    .setCategory(Notification.CATEGORY_CAR_WARNING)
                    .setColor(mContext.getColor(android.R.color.holo_orange_dark))
                    .setColorized(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

        view.findViewById(R.id.category_car_info_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Car information")
                    .setContentText("Doesn't show heads-up; Importance Default; Groups")
                    .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                    .setColor(mContext.getColor(android.R.color.holo_orange_light))
                    .setColorized(true)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });

    }

    private void initImportanceHighBotton(View view) {
        Intent mIntent = new Intent(mContext, KitchenSinkActivity.class);
        PendingIntent mPendingIntent = PendingIntent.getActivity(mContext, 0, mIntent, 0);

        Notification notification1 = new Notification
                .Builder(mContext, IMPORTANCE_HIGH_ID)
                .setContentTitle("Importance High: Shows as a heads-up")
                .setContentText(
                        "Each click generates a new notification. And some "
                                + "looooooong text. "
                                + "Loooooooooooooooooooooong. "
                                + "Loooooooooooooooooooooooooooooooooooooooooooooooooong.")
                .setSmallIcon(R.drawable.car_ic_mode)
                .addAction(
                        new Notification.Action.Builder(
                                null, "Long Action (no-op)", mPendingIntent).build())
                .addAction(
                        new Notification.Action.Builder(
                                null, "Action (no-op)", mPendingIntent).build())
                .addAction(
                        new Notification.Action.Builder(
                                null, "Long Action (no-op)", mPendingIntent).build())
                .setColor(mContext.getColor(android.R.color.holo_red_light))
                .build();

        view.findViewById(R.id.importance_high_button).setOnClickListener(
                v -> mManager.notify(mCurrentNotificationId++, notification1)
        );
    }

    private void initImportanceDefaultButton(View view) {
        view.findViewById(R.id.importance_default_button).setOnClickListener(v -> {
            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("No heads-up; Importance Default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initImportanceLowButton(View view) {
        view.findViewById(R.id.importance_low_button).setOnClickListener(v -> {

            Notification notification = new Notification.Builder(mContext, IMPORTANCE_LOW_ID)
                    .setContentTitle("Importance Low")
                    .setContentText("No heads-up; Below Importance Default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initImportanceMinButton(View view) {
        view.findViewById(R.id.importance_min_button).setOnClickListener(v -> {

            Notification notification = new Notification.Builder(mContext, IMPORTANCE_MIN_ID)
                    .setContentTitle("Importance Min")
                    .setContentText("No heads-up; Below Importance Low; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initOngoingButton(View view) {
        view.findViewById(R.id.ongoing_button).setOnClickListener(v -> {

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Persistent/Ongoing Notification")
                    .setContentText("Cannot be dismissed; No heads-up; Importance default; Groups")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setOngoing(true)
                    .build();
            mManager.notify(mCurrentNotificationId++, notification);
        });
    }

    private void initMessagingStyleButton(View view) {
        view.findViewById(R.id.category_message_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            PendingIntent replyIntent = createServiceIntent(id, "reply");
            PendingIntent markAsReadIntent = createServiceIntent(id, "read");

            Person personJohn = new Person.Builder().setName("John Doe").build();
            Person personJane = new Person.Builder().setName("Jane Roe").build();
            MessagingStyle messagingStyle =
                    new MessagingStyle(personJohn)
                            .setConversationTitle("Heads-up: New Message")
                            .addMessage(
                                    new MessagingStyle.Message(
                                            "The meaning of life, or the answer to the question"
                                                    + "What is the meaning of life?, pertains to "
                                                    + "the significance of living or existence in"
                                                    + " general. Many other related questions "
                                                    + "include: Why are we here?, What is "
                                                    + "life all about?, or What is the "
                                                    + "purpose of existence?",
                                            System.currentTimeMillis() - 3600,
                                            personJohn))
                            .addMessage(
                                    new MessagingStyle.Message(
                                            "Importance High; Groups; Each click generates a new"
                                                    + "notification. And some looooooong text. "
                                                    + "Loooooooooooooooooooooong. "
                                                    + "Loooooooooooooooooooooooooong."
                                                    + "Long long long long text.",
                                            System.currentTimeMillis(),
                                            personJane));

            NotificationCompat.Builder notification = new NotificationCompat
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setContentTitle("Message from someone")
                    .setContentText("hi")
                    .setShowWhen(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .setStyle(messagingStyle)
                    .setAutoCancel(true)
                    .setColor(mContext.getColor(android.R.color.holo_green_light))
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "read", markAsReadIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_MARK_AS_READ)
                                    .setShowsUserInterface(false)
                                    .build())
                    .addAction(
                            new Action.Builder(R.drawable.ic_check_box, "reply", replyIntent)
                                    .setSemanticAction(Action.SEMANTIC_ACTION_REPLY)
                                    .setShowsUserInterface(false)
                                    .addRemoteInput(new RemoteInput.Builder("input").build())
                                    .build());

            mManager.notify(id, notification.build());
        });
    }

    private PendingIntent createServiceIntent(int notificationId, String action) {
        Intent intent = new Intent(mContext, KitchenSinkActivity.class).setAction(action);

        return PendingIntent.getForegroundService(mContext, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void initProgressButton(View view) {
        view.findViewById(R.id.progress_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                    .setContentTitle("Progress")
                    .setOngoing(true)
                    .setContentText(
                            "Doesn't show heads-up; Importance Default; Groups; Ongoing (cannot "
                                    + "be dismissed)")
                    .setProgress(100, 0, false)
                    .setColor(mContext.getColor(android.R.color.holo_purple))
                    .setContentInfo("0%")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(id, notification);

            Runnable runnable = new Runnable() {
                int mProgress = 0;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_DEFAULT_ID)
                            .setContentTitle("Progress")
                            .setContentText("Doesn't show heads-up; Importance Default; Groups")
                            .setProgress(100, mProgress, false)
                            .setOngoing(true)
                            .setColor(mContext.getColor(android.R.color.holo_purple))
                            .setContentInfo(mProgress + "%")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .build();
                    mManager.notify(id, updateNotification);
                    mProgress += 5;
                    if (mProgress <= 100) {
                        mHandler.postDelayed(this, 1000);
                    }
                }
            };
            mUpdateRunnables.put(id, runnable);
            mHandler.post(runnable);
        });
    }

    private void initNavigationButton(View view) {
        view.findViewById(R.id.navigation_button).setOnClickListener(v -> {
            int id = mCurrentNotificationId++;

            Notification notification = new Notification
                    .Builder(mContext, IMPORTANCE_HIGH_ID)
                    .setCategory(Notification.CATEGORY_NAVIGATION)
                    .setContentTitle("Navigation")
                    .setContentText("Turn right in 900 ft")
                    .setColor(mContext.getColor(android.R.color.holo_green_dark))
                    .setColorized(true)
                    .setSubText("900 ft")
                    .setSmallIcon(R.drawable.car_ic_mode)
                    .build();
            mManager.notify(id, notification);

            Runnable rightTurnRunnable = new Runnable() {
                int mDistance = 800;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_HIGH_NO_SOUND_ID)
                            .setCategory(Notification.CATEGORY_NAVIGATION)
                            .setContentTitle("Navigation")
                            .setContentText("Turn right in " + mDistance + " ft")
                            .setColor(mContext.getColor(android.R.color.holo_green_dark))
                            .setColorized(true)
                            .setSubText(mDistance + " ft")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .build();
                    mManager.notify(id, updateNotification);
                    mDistance -= 100;
                    if (mDistance >= 0) {
                        mHandler.postDelayed(this, 1000);
                    }
                }
            };

            Runnable exitRunnable = new Runnable() {
                int mDistance = 9;

                @Override
                public void run() {
                    Notification updateNotification = new Notification
                            .Builder(mContext, IMPORTANCE_HIGH_NO_SOUND_ID)
                            .setCategory(Notification.CATEGORY_NAVIGATION)
                            .setContentTitle("Navigation")
                            .setContentText("Exit in " + mDistance + " miles")
                            .setColor(mContext.getColor(android.R.color.holo_green_dark))
                            .setColorized(true)
                            .setSubText(mDistance + " miles")
                            .setSmallIcon(R.drawable.car_ic_mode)
                            .build();
                    mManager.notify(id, updateNotification);
                    mDistance -= 1;
                    if (mDistance >= 0) {
                        mHandler.postDelayed(this, 1000);
                    }
                }
            };

            mUpdateRunnables.put(id, rightTurnRunnable);
            mUpdateRunnables.put(id, exitRunnable);
            mHandler.postDelayed(rightTurnRunnable, 1000);
            mHandler.postDelayed(exitRunnable, 10000);
        });
    }
}
