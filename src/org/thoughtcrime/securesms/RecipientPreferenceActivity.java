package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;

import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEventCenter;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.preferences.CorrectedPreferenceFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.Prefs.VibrateState;
import org.thoughtcrime.securesms.util.ViewUtil;

@SuppressLint("StaticFieldLeak")
public class RecipientPreferenceActivity extends PassphraseRequiredActionBarActivity implements DcEventCenter.DcEventDelegate
{
  private static final String TAG = RecipientPreferenceActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA                = "recipient_address";

  private static final String PREFERENCE_MUTED           = "pref_key_recipient_mute";
  private static final String PREFERENCE_MESSAGE_TONE    = "pref_key_recipient_ringtone";
  private static final String PREFERENCE_MESSAGE_VIBRATE = "pref_key_recipient_vibrate";
  private static final String PREFERENCE_BLOCK           = "pref_key_recipient_block";
  private static final String PREFERENCE_IDENTITY        = "pref_key_recipient_identity";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ImageView               avatar;
  private GlideRequests           glideRequests;
  private Address                 address;
  private CollapsingToolbarLayout toolbarLayout;

  private ApplicationDcContext dcContext;

  private static final int REQUEST_CODE_PICK_RINGTONE = 1;

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle instanceState, boolean ready) {
    dcContext = DcHelper.getContext(this);

    setContentView(R.layout.recipient_preference_activity);
    this.glideRequests = GlideApp.with(this);
    this.address       = getIntent().getParcelableExtra(ADDRESS_EXTRA);

    Recipient recipient = Recipient.from(this, address, true);

    initializeToolbar();
    setHeader(recipient);

    Bundle bundle = new Bundle();
    bundle.putParcelable(ADDRESS_EXTRA, address);
    initFragment(R.id.preference_fragment, new RecipientPreferenceFragment(), null, bundle);

    dcContext.eventCenter.addObserver(DcContext.DC_EVENT_CONTACTS_CHANGED, this);
  }

  @Override
  protected void onDestroy() {
    dcContext.eventCenter.removeObservers(this);
    super.onDestroy();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.preference_fragment);
    fragment.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void handleEvent(int eventId, Object data1, Object data2) {
    if(eventId==DcContext.DC_EVENT_CONTACTS_CHANGED) {
      Recipient recipient = Recipient.from(this, address, true);
      setHeader(recipient);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        onBackPressed();
        return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    finish();
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
  }

  private void initializeToolbar() {
    this.toolbarLayout        = ViewUtil.findById(this, R.id.collapsing_toolbar);
    this.avatar               = ViewUtil.findById(this, R.id.avatar);

    this.toolbarLayout.setExpandedTitleColor(getResources().getColor(R.color.white));
    this.toolbarLayout.setCollapsedTitleTextColor(getResources().getColor(R.color.white));

    Toolbar toolbar = ViewUtil.findById(this, R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setLogo(null);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
      getWindow().setStatusBarColor(Color.TRANSPARENT);
    }
  }

  private void setHeader(@NonNull Recipient recipient) {
    glideRequests.load(recipient.getContactPhoto(this))
                 .fallback(recipient.getFallbackContactPhoto().asCallCard(this))
                 .error(recipient.getFallbackContactPhoto().asCallCard(this))
                 .diskCacheStrategy(DiskCacheStrategy.ALL)
                 .into(this.avatar);

    if (recipient.getContactPhoto(this) == null) this.avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    else                                     this.avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);

    this.avatar.setBackgroundColor(recipient.getFallbackAvatarColor(this));
    this.toolbarLayout.setTitle(recipient.toShortString());
    this.toolbarLayout.setContentScrimColor(recipient.getFallbackAvatarColor(this));
  }

  public static class RecipientPreferenceFragment
      extends    CorrectedPreferenceFragment
      implements DcEventCenter.DcEventDelegate
  {
    private ApplicationDcContext dcContext;
    private Recipient recipient;

    @Override
    public void onCreate(Bundle icicle) {
      dcContext = DcHelper.getContext(getActivity());
      super.onCreate(icicle);

      this.recipient = Recipient.from(getActivity(), getArguments().getParcelable(ADDRESS_EXTRA), true);

      this.findPreference(PREFERENCE_MESSAGE_TONE)
          .setOnPreferenceChangeListener(new RingtoneChangeListener());
      this.findPreference(PREFERENCE_MESSAGE_TONE)
          .setOnPreferenceClickListener(new RingtoneClickedListener());
      this.findPreference(PREFERENCE_MESSAGE_VIBRATE)
          .setOnPreferenceChangeListener(new VibrateChangeListener());
      this.findPreference(PREFERENCE_MUTED)
          .setOnPreferenceClickListener(new MuteClickedListener());
      this.findPreference(PREFERENCE_BLOCK)
          .setOnPreferenceClickListener(new BlockClickedListener());

      dcContext.eventCenter.addObserver(DcContext.DC_EVENT_CONTACTS_CHANGED, this);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
      Log.w(TAG, "onCreatePreferences...");
      addPreferencesFromResource(R.xml.recipient_preferences);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
      Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

    @Override
    public void onResume() {
      super.onResume();
      setSummaries(recipient);
    }

    @Override
    public void onDestroy() {
      dcContext.eventCenter.removeObservers(this);
      super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
      if (requestCode == REQUEST_CODE_PICK_RINGTONE && resultCode == RESULT_OK && data != null) {
        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

        findPreference(PREFERENCE_MESSAGE_TONE).getOnPreferenceChangeListener().onPreferenceChange(findPreference(PREFERENCE_MESSAGE_TONE), uri);
      }
    }

    private void setSummaries(Recipient recipient) {
      int chatId = recipient.getAddress().isDcChat()? recipient.getAddress().getDcChatId() : 0;

      CheckBoxPreference    mutePreference            = (CheckBoxPreference) this.findPreference(PREFERENCE_MUTED);
      Preference            ringtoneMessagePreference = this.findPreference(PREFERENCE_MESSAGE_TONE);
      ListPreference        vibrateMessagePreference  = (ListPreference) this.findPreference(PREFERENCE_MESSAGE_VIBRATE);
      Preference            blockPreference           = this.findPreference(PREFERENCE_BLOCK);
      Preference            identityPreference        = this.findPreference(PREFERENCE_IDENTITY);
      PreferenceCategory    aboutCategory             = (PreferenceCategory)this.findPreference("about");
      PreferenceCategory    aboutDivider              = (PreferenceCategory)this.findPreference("about_divider");
      PreferenceCategory    privacyCategory           = (PreferenceCategory) this.findPreference("privacy_settings");
      PreferenceCategory    divider                   = (PreferenceCategory) this.findPreference("divider");

      mutePreference.setChecked(Prefs.isChatMuted(getContext(), chatId));

      ringtoneMessagePreference.setSummary(getRingtoneSummary(getContext(), recipient.getMessageRingtone()));

      VibrateState vibrateState = Prefs.getChatVibrate(getContext(), chatId);
      Pair<String, Integer> vibrateMessageSummary = getVibrateSummary(getContext(), vibrateState);

      vibrateMessagePreference.setSummary(vibrateMessageSummary.first);
      vibrateMessagePreference.setValueIndex(vibrateMessageSummary.second);

      if (recipient.isGroupRecipient()) {
        if (blockPreference    != null) blockPreference.setVisible(false);
        if (identityPreference != null) identityPreference.setVisible(false);
        if (privacyCategory    != null) privacyCategory.setVisible(false);
        if (divider            != null) divider.setVisible(false);
        if (aboutCategory      != null) getPreferenceScreen().removePreference(aboutCategory);
        if (aboutDivider       != null) getPreferenceScreen().removePreference(aboutDivider);
      } else {
        if (recipient.isBlocked()) blockPreference.setTitle(R.string.RecipientPreferenceActivity_unblock);
        else                       blockPreference.setTitle(R.string.RecipientPreferenceActivity_block);

//        IdentityUtil.getRemoteIdentityKey(getActivity(), recipient).addListener(new ListenableFuture.Listener<Optional<IdentityRecord>>() {
//          @Override
//          public void onSuccess(Optional<IdentityRecord> result) {
//            if (result.isPresent()) {
//              if (identityPreference != null) identityPreference.setOnPreferenceClickListener(new IdentityClickedListener(result.get()));
//              if (identityPreference != null) identityPreference.setEnabled(true);
//            } else if (canHaveSafetyNumber) {
//              if (identityPreference != null) identityPreference.setSummary(R.string.RecipientPreferenceActivity_available_once_a_message_has_been_sent_or_received);
//              if (identityPreference != null) identityPreference.setEnabled(false);
//            } else {
//              if (identityPreference != null) getPreferenceScreen().removePreference(identityPreference);
//            }
//          }
//
//          @Override
//          public void onFailure(ExecutionException e) {
//            if (identityPreference != null) getPreferenceScreen().removePreference(identityPreference);
//          }
//        });
        getPreferenceScreen().removePreference(identityPreference);
      }
    }

    private @NonNull String getRingtoneSummary(@NonNull Context context, @Nullable Uri ringtone) {
      if (ringtone == null) {
        return context.getString(R.string.preferences__default);
      } else if (ringtone.toString().isEmpty()) {
        return context.getString(R.string.preferences__silent);
      } else {
        Ringtone tone = RingtoneManager.getRingtone(getActivity(), ringtone);

        if (tone != null) {
          return tone.getTitle(context);
        }
      }

      return context.getString(R.string.preferences__default);
    }

    private @NonNull Pair<String, Integer> getVibrateSummary(@NonNull Context context, @NonNull VibrateState vibrateState) {
      if (vibrateState == VibrateState.DEFAULT) {
        return new Pair<>(context.getString(R.string.preferences__default), 0);
      } else if (vibrateState == VibrateState.ENABLED) {
        return new Pair<>(context.getString(R.string.RecipientPreferenceActivity_enabled), 1);
      } else {
        return new Pair<>(context.getString(R.string.RecipientPreferenceActivity_disabled), 2);
      }
    }

    @Override
    public void handleEvent(int eventId, Object data1, Object data2) {
      if(eventId==DcContext.DC_EVENT_CONTACTS_CHANGED) {
        setSummaries(recipient);
      }
    }

    private class RingtoneChangeListener implements Preference.OnPreferenceChangeListener {

      RingtoneChangeListener() {
      }

      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        Uri value = (Uri)newValue;

        Uri defaultValue = Prefs.getNotificationRingtone(getContext());

        if (defaultValue.equals(value)) value = null;
        else if (value == null)         value = Uri.EMPTY;

        int chatId = recipient.getAddress().isDcChat()? recipient.getAddress().getDcChatId() : 0;
        Prefs.setChatRingtone(getContext(), chatId, value);

        return false;
      }
    }

    private class RingtoneClickedListener implements Preference.OnPreferenceClickListener {

      RingtoneClickedListener() {
      }

      @Override
      public boolean onPreferenceClick(Preference preference) {
        Uri current = recipient.getMessageRingtone();
        Uri defaultUri = Prefs.getNotificationRingtone(getContext());

        if      (current == null)              current = Settings.System.DEFAULT_NOTIFICATION_URI;
        else if (current.toString().isEmpty()) current = null;

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);

        startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);

        return true;
      }
    }

    private class VibrateChangeListener implements Preference.OnPreferenceChangeListener {

      VibrateChangeListener() {
      }

      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
              int          value        = Integer.parseInt((String) newValue);
        final VibrateState vibrateState = VibrateState.fromId(value);

        int chatId = recipient.getAddress().isDcChat()? recipient.getAddress().getDcChatId() : 0;
        Prefs.setChatVibrate(getContext(), chatId, vibrateState);

        return false;
      }
    }

    private class MuteClickedListener implements Preference.OnPreferenceClickListener {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        int chatId = recipient.getAddress().isDcChat()? recipient.getAddress().getDcChatId() : 0;

        if (Prefs.isChatMuted(getContext(), chatId)) {
          handleUnmute();
        }
        else {
          handleMute();
        }

        return true;
      }

      private void handleMute() {
        MuteDialog.show(getActivity(), until -> setMuted(recipient, until));
      }

      private void handleUnmute() {
        setMuted(recipient, 0);
      }

      private void setMuted(final Recipient recipient, final long until) {
        if(recipient.getAddress().isDcChat()) {
          Prefs.setChatMutedUntil(getActivity(), recipient.getAddress().getDcChatId(), until);
          setSummaries(recipient);
        }
      }
    }

    private class BlockClickedListener implements Preference.OnPreferenceClickListener {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        if (recipient.isBlocked()) handleUnblock();
        else                       handleBlock();

        return true;
      }

      private void handleBlock() {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.RecipientPreferenceActivity_block_this_contact_question)
            .setMessage(R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_block, (dialog, which) -> setBlocked(recipient, true)).show();
      }

      private void handleUnblock() {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
            .setMessage(R.string.RecipientPreferenceActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_unblock, (dialog, which) -> setBlocked(recipient, false)).show();
      }

      private void setBlocked(final Recipient recipient, final boolean blocked) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            ApplicationDcContext dcContext = DcHelper.getContext(getContext());
            int[] contactId = dcContext.getChatContacts(recipient.getAddress().getDcChatId());
            dcContext.blockContact(contactId[0], blocked ? 1 : 0);
            return null;
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }
    }
  }
}
