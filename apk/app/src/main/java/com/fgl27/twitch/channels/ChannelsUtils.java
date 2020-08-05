/*
 * Copyright (c) 2017-2020 Felipe de Leon <fglfgl27@gmail.com>
 *
 * This file is part of SmartTwitchTV <https://github.com/fgl27/SmartTwitchTV>
 *
 * SmartTwitchTV is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SmartTwitchTV is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SmartTwitchTV.  If not, see <https://github.com/fgl27/SmartTwitchTV/blob/master/LICENSE>.
 *
 */

package com.fgl27.twitch.channels;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.tvprovider.media.tv.Channel;
import androidx.tvprovider.media.tv.ChannelLogoUtils;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.TvContractCompat;

import com.fgl27.twitch.Constants;
import com.fgl27.twitch.PlayerActivity;
import com.fgl27.twitch.R;
import com.fgl27.twitch.Tools;
import com.fgl27.twitch.notification.NotificationUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.grandcentrix.tray.AppPreferences;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.google.gson.JsonParser.parseString;

public final class ChannelsUtils {

    private static final int JOB_ID = 1;

    private static final String TAG = "STTV_ChannelsUtils";

    private static final String[] TV_CONTRACT_ARRAY = {
            TvContractCompat.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NAME
    };

    private static final ChannelContentObj emptyContent =
            new ChannelContentObj(
                    "Empty list",
                    "This channel was disabled or on last refresh it failed to load content. Press enter to refresh this (refresh only happens when the app is visible, so click here will open the app)",
                    "https://fgl27.github.io/SmartTwitchTV/release/githubio/images/refresh.png",
                    TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1,
                    1,
                    null,
                    false
            );

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    public static class PreviewObj {
        private final JsonObject obj;
        private final String type;
        private final int screen;

        PreviewObj(JsonObject obj, String type, int screen) {
            this.obj = obj;
            this.type = type;
            this.screen = screen;
        }

        PreviewObj(int screen, String type) {
            this.obj = null;
            this.type = type;
            this.screen = screen;
        }
    }

    public static class ChannelContentObj {
        private final String title;
        private final String description;
        private final String imgUrl;
        private final int previewSize;
        private final int viewers;
        private final String obj;
        private final boolean isLive;

        ChannelContentObj(String title, String description, String imgUrl, int previewSize, int viewers, String obj, boolean isLive) {
            this.title = title;
            this.description = description;
            this.imgUrl = imgUrl;
            this.previewSize = previewSize;
            this.viewers = viewers;
            this.obj = obj;
            this.isLive = isLive;
        }

    }

    private static Comparator<ChannelContentObj> compareViewers = (Obj1, Obj2) -> Obj2.viewers - Obj1.viewers;

    public static class ChannelObj {
        private final int drawable;
        private final String name;
        private final int type;
        private final List<ChannelContentObj> Content;

        ChannelObj(int drawable, String name, int type, List<ChannelContentObj> content) {
            this.drawable = drawable;
            this.name = name;
            this.type = type;
            this.Content = content;
        }

    }

    private static void StartChannel(Context context, ChannelObj channel, long channelId) {

        if (channelId != -1L) {//Channel already created just update
            updateChannel(context, channelId, channel);
            createChannelContent(context, channelId, channel);
            return;
        }

        channelId = createChannel(context, channel);

        if (channelId != -1L) {

            createChannelContent(context, channelId, channel);

            //Default channel
            if (Objects.equals(channel.name, Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_LIVE]))
                TvContractCompat.requestChannelBrowsable(context, channelId);

        }
    }

    private static void updateChannel(Context context, long channelId, ChannelObj channel) {
        writeChannelLogo(context, channelId, channel.drawable);

        Channel.Builder builder = createChannelBuilder(channel.name, channel.type, context);

        int rowsUpdated = context.getContentResolver().update(
                TvContractCompat.buildChannelUri(channelId), builder.build().toContentValues(), null, null);

        if (rowsUpdated < 1) {
            Log.w(TAG, "Update channel failed " + channel.name);
        }
    }

    private static long createChannel(Context context, ChannelObj channel) {
        Channel.Builder builder = createChannelBuilder(channel.name, channel.type, context);

        Uri channelUri = null;

        try {

            channelUri = context.getContentResolver()
                    .insert(
                            TvContractCompat.Channels.CONTENT_URI,
                            builder.build().toContentValues()
                    );

        } catch (Exception e) { // channels not supported
            Log.w(TAG, "createChannel e " , e);
        }

        if (channelUri == null || channelUri.equals(Uri.EMPTY)) {
            Log.w(TAG, "Insert channel failed " + channel.name);
            return -1L;
        }

        long channelId = ContentUris.parseId(channelUri);

        writeChannelLogo(context, channelId, channel.drawable);

        return channelId;
    }

    private static void createChannelContent(Context context, long channelId, ChannelObj channel) {
        DeleteProgram(context, channelId);

        int channel_type = channel.type;
        String randomImg = "?" + ThreadLocalRandom.current().nextInt(1, 1000000);
        List<ChannelContentObj> Content = channel.Content;

        if (Content != null) {
            int ContentSize = Content.size();
            int weight = ContentSize;

            for (int i = 0; i < ContentSize; i++, --weight) {
                PreviewProgramAdd(context, channelId, Content.get(i), weight, channel_type, randomImg);
            }

        } else {
            PreviewProgramAdd(context, channelId, emptyContent, 0, channel_type, randomImg);
        }

    }

    private static void PreviewProgramAdd(Context context, long channelId, ChannelContentObj ContentObj, int weight, int channel_type, String randomImg) {
        PreviewProgram.Builder builder =
                new PreviewProgram.Builder()
                        .setTitle(ContentObj.title)
                        .setDescription(ContentObj.description)
                        .setPosterArtUri(Uri.parse(ContentObj.imgUrl + randomImg))
                        .setPosterArtAspectRatio(ContentObj.previewSize)
                        .setIntent(createAppIntent(context, ContentObj.obj, channel_type))
                        .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                        .setLive(ContentObj.isLive)
                        .setWeight(weight)
                        .setChannelId(channelId);

        Uri programUri = null;

        try {
            programUri = context.getContentResolver().insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    builder.build().toContentValues()
            );
        } catch (Exception e) {
            Log.w(TAG, "programUri e ", e);
        }

        if (programUri == null || programUri.equals(Uri.EMPTY)) {
            Log.w(TAG, "Insert program failed " + ContentObj.title);
        }
    }

    private static Channel.Builder createChannelBuilder(String name, int channel_screen, Context context) {

        return new Channel.Builder()
                .setDisplayName(name)
                .setAppLinkIntent(createAppIntent(context, new Gson().toJson(new PreviewObj(channel_screen, "SCREEN")), 0))
                .setType(TvContractCompat.Channels.TYPE_PREVIEW);

    }

    private static Intent createAppIntent(Context context, String channel_obj, int channel_type) {

        Intent intent = new Intent(context, PlayerActivity.class);
        intent.setAction(Constants.CHANNEL_INTENT);
        intent.putExtra(Constants.CHANNEL_TYPE, channel_type);
        intent.putExtra(Constants.CHANNEL_OBJ, channel_obj);

        return intent;
    }

    static private void writeChannelLogo(Context context, long channelId, @DrawableRes int drawableId) {
        if (channelId != -1 && drawableId != -1) {

            ChannelLogoUtils.storeChannelLogo(
                    context,
                    channelId,
                    convertToBitmap(context, drawableId)
            );

        }
    }

    @NonNull
    private static Bitmap convertToBitmap(Context context, int resourceId) {
        Drawable drawable = context.getDrawable(resourceId);
        if (drawable instanceof VectorDrawable) {

            Bitmap bitmap =
                    Bitmap.createBitmap(
                            drawable.getIntrinsicWidth(),
                            drawable.getIntrinsicHeight(),
                            Bitmap.Config.ARGB_8888
                    );

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        }

        return BitmapFactory.decodeResource(context.getResources(), resourceId);
    }

    private static void DeleteProgram(Context context, long channelId) {
        if (channelId != -1L) {

            context.getContentResolver().delete(
                    TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                    null,
                    null
            );

        }
    }

    private static long getChannelIdFromTvProvider(Context context, String channel_name) {
        Cursor cursor =
                context.getContentResolver()
                        .query(
                                TvContractCompat.Channels.CONTENT_URI,
                                TV_CONTRACT_ARRAY,
                                null,
                                null,
                                null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Channel channel = Channel.fromCursor(cursor);
                if (channel_name.equals(channel.getDisplayName())) {
                    return channel.getId();
                }
            } while (cursor.moveToNext());

            cursor.close();
        }

        return -1L;
    }

    private static boolean getChannelIsBrowsable(Context context, long channelId) {
        try (Cursor cursor =
                     context.getContentResolver()
                             .query(
                                     TvContractCompat.buildChannelUri(channelId),
                                     null,
                                     null,
                                     null,
                                     null)) {
            if (cursor != null && cursor.moveToNext()) {
                Channel channel = Channel.fromCursor(cursor);
                return channel.isBrowsable();
            }
        }

        return false;
    }

    public static void scheduleSyncingChannel(Context context) {

        try {

            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

            scheduler.schedule(
                    new JobInfo.Builder(JOB_ID, new ComponentName(context, SyncChannelJobService.class))
                            .setPeriodic(TimeUnit.MINUTES.toMillis(30))
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setRequiresDeviceIdle(false)
                            .setRequiresCharging(false)
                            .build()
            );

        } catch (Exception e) {
            Log.w(TAG, "scheduleSyncingChannel ", e);
        }

    }

    public static boolean isJobServiceNotSchedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        for ( JobInfo jobInfo : scheduler.getAllPendingJobs() ) {
            if ( jobInfo.getId() == JOB_ID ) {
                return false;
            }
        }

        return true ;
    }

    private static ChannelContentObj getNoUserContent(int screen) {
        return
                new ChannelContentObj(
                        "Add User",
                        "Is necessary to add a user first to load this content",
                        "https://fgl27.github.io/SmartTwitchTV/release/githubio/images/add_user.png",
                        TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1,
                        1,
                        new Gson().toJson(new PreviewObj(null, "USER", screen)),
                        false
                );
    }

    private static ChannelContentObj getRefreshContent() {
        Calendar rightNow = Calendar.getInstance();
        String month = rightNow.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        String day = String.valueOf(rightNow.get(Calendar.DAY_OF_MONTH));
        String dayMonth;

//        @RequiresApi(api = Build.VERSION_CODES.O)
//        String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
//                FormatStyle.SHORT,
//                FormatStyle.SHORT,
//                IsoChronology.INSTANCE,
//                Locale.getDefault()
//        ).replaceAll("[^Md]|(?<=(.))\\1", "").toUpperCase();

        String dateFormat = ((SimpleDateFormat) DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()))
                .toPattern().replaceAll("[^Md]|(?<=(.))\\1", "").toUpperCase();

        if (dateFormat.startsWith("M")) dayMonth = month + " " + day;
        else dayMonth = day + " " + month;

        return new ChannelContentObj(
                String.format(
                        Locale.US,
                        "Last refresh - %s %s %02d:%02d",
                        rightNow.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()),
                        dayMonth,
                        rightNow.get(Calendar.HOUR_OF_DAY),
                        rightNow.get(Calendar.MINUTE)
                ),
                "Press enter to refresh this, a manual refresh can only happen when the app is visible, so clicking here will open the app, this channel auto refresh it 30 minutes",
                "https://fgl27.github.io/SmartTwitchTV/release/githubio/images/refresh.png",
                TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1,
                1,
                null,
                false
        );

    }

    public static DecimalFormat getDecimalFormat() {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        decimalFormat.setGroupingUsed(true);
        decimalFormat.setGroupingSize(3);

        return decimalFormat;
    }

    private static String getTimeFromMs(long millis) {
        if(millis < 0) return null;

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        return String.format(Locale.US, "since %02d:%02d:%02d", hours, minutes, seconds);
    }

    public static void UpdateAllChannels(Context context, AppPreferences appPreferences) {
        StartLive(context);
        StartFeatured(context);
        StartGames(context);
        UpdateUserChannels(context, appPreferences);
    }

    public static void UpdateUserChannels(Context context, AppPreferences appPreferences) {
        ChannelsUtils.SetUserLive(
                context,
                Tools.getString(Constants.PREF_USER_ID, null, appPreferences),
                appPreferences
        );
        ChannelsUtils.StartUserGames(
                context,
                Tools.getString(Constants.PREF_USER_NAME, null, appPreferences)
        );
        ChannelsUtils.StartUserHost(
                context,
                Tools.getString(Constants.PREF_USER_NAME, null, appPreferences)
        );
    }

    public static void StartLive(Context context) {

        String lang = Tools.getString(Constants.PREF_USER_LANGUAGE, null, new AppPreferences(context));

        List<ChannelContentObj> content = null;
        long channelId = getChannelIdFromTvProvider(
                context,
                Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_LIVE]
        );

        //this is the default channel add content without check for getChannelIsBrowsable
        if (channelId == -1L || getChannelIsBrowsable(context, channelId)) {

            content = GetLiveContent(
                    "https://api.twitch.tv/kraken/streams?limit=100&offset=0&api_version=5" + (lang != null ? "&language=" + lang : ""),
                    "streams",
                    null,
                    true,
                    Constants.CHANNEL_TYPE_LIVE
            );

        }

        StartChannel(
                context,
                new ChannelObj(
                        R.mipmap.ic_launcher,
                        Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_LIVE],
                        Constants.CHANNEL_TYPE_LIVE,
                        content
                ),
                channelId
        );
    }

    public static void SetUserLive(Context context, String userId, AppPreferences appPreferences) {
        long channelId = getChannelIdFromTvProvider(
                context,
                Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_USER_LIVE]
        );

        if (userId != null) {

            if (channelId != -1L && getChannelIsBrowsable(context, channelId)) {
                JsonArray Streams = NotificationUtils.GetLiveStreamsList(userId, appPreferences);

                if (Streams != null) {

                    StartUserLive(
                            context,
                            ProcessLiveArray(
                                    Streams,//Get the follows array
                                    null,
                                    true,
                                    Constants.CHANNEL_TYPE_USER_LIVE
                            ),
                            channelId
                    );

                }

                return;
            }

            StartUserLive(context, null, channelId);
        } else {

            List<ChannelContentObj> content = new ArrayList<>();
            content.add(getNoUserContent(Constants.CHANNEL_TYPE_USER_LIVE));
            StartUserLive(context, content, channelId);

        }
    }

    private static void StartUserLive(Context context, List<ChannelContentObj> contentObj, long channelId) {
        StartChannel(
                context,
                new ChannelObj(
                        R.mipmap.ic_launcher,
                        Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_USER_LIVE],
                        Constants.CHANNEL_TYPE_USER_LIVE,
                        contentObj
                ),
                channelId
        );
    }

    public static void StartUserHost(Context context, String name) {
        long channelId = getChannelIdFromTvProvider(
                context,
                Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_USER_HOST]
        );

        List<ChannelContentObj> content = null;

        if (name != null) {

            if (channelId != -1L && getChannelIsBrowsable(context, channelId)) {

                String url = String.format(
                        Locale.US,
                        "https://api.twitch.tv/api/users/%s/followed/hosting?limit=100",
                        name
                );

                content = GetHostContent(url);

            }
        } else {

            content = new ArrayList<>();
            content.add(getNoUserContent(Constants.CHANNEL_TYPE_USER_HOST));

        }

        StartChannel(
                context,
                new ChannelObj(
                        R.mipmap.ic_launcher,
                        Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_USER_HOST],
                        Constants.CHANNEL_TYPE_USER_HOST,
                        content
                ),
                channelId
        );
    }

    public static void StartFeatured(Context context) {
        List<ChannelContentObj> content = null;

        long channelId = getChannelIdFromTvProvider(
                context,
                Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_FEATURED]
        );

        if (channelId != -1L && getChannelIsBrowsable(context, channelId)) {

            content = GetLiveContent(
                    "https://api.twitch.tv/kraken/streams/featured?limit=100&offset=0&api_version=5",
                    "featured",
                    "stream",
                    false,
                    Constants.CHANNEL_TYPE_FEATURED
            );

        }

        StartChannel(
                context,
                new ChannelObj(
                        R.mipmap.ic_launcher,
                        Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_FEATURED],
                        Constants.CHANNEL_TYPE_FEATURED,
                        content
                ),
                channelId
        );
    }

    public static void StartGames(Context context) {
        long channelId = getChannelIdFromTvProvider(
                context,
                Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_GAMES]
        );
        List<ChannelContentObj> content = null;

        if (channelId != -1L && getChannelIsBrowsable(context, channelId)) {

            content = GetGamesContent(
                    "https://api.twitch.tv/kraken/games/top?limit=100&offset=0&api_version=5",
                    "top",
                    Constants.DEFAULT_HEADERS,
                    Constants.CHANNEL_TYPE_GAMES
            );

            if (content != null) {

                int contentSize = content.size();

                if (contentSize > 1) {
                    Collections.sort(
                            content.subList(1, contentSize),
                            compareViewers
                    );
                }

            }
        }

        StartChannel(
                context,
                new ChannelObj(
                        R.mipmap.ic_launcher,
                        Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_GAMES],
                        Constants.CHANNEL_TYPE_GAMES,
                        content
                ),
                channelId
        );
    }

    public static void StartUserGames(Context context, String name) {
        long channelId = getChannelIdFromTvProvider(
                context,
                Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_USER_GAMES]
        );

        List<ChannelContentObj> content = null;

        if (name != null) {

            if (channelId != -1L && getChannelIsBrowsable(context, channelId)) {
                String url = String.format(
                        Locale.US,
                        "https://api.twitch.tv/api/users/%s/follows/games/live?limit=250",
                        name
                );

                content = GetGamesContent(
                        url,
                        "follows",
                        new String[0][0],
                        Constants.CHANNEL_TYPE_USER_GAMES
                );
            }
        } else {

            content = new ArrayList<>();
            content.add(getNoUserContent(Constants.CHANNEL_TYPE_USER_GAMES));

        }

        StartChannel(
                context,
                new ChannelObj(
                        R.mipmap.ic_launcher,
                        Constants.CHANNELS_NAMES[Constants.CHANNEL_TYPE_USER_GAMES],
                        Constants.CHANNEL_TYPE_USER_GAMES,
                        content
                ),
                channelId
        );
    }

    private static List<ChannelContentObj> GetHostContent(String url)  {

        try {
            Tools.ResponseObj response;

            for (int i = 0; i < 3; i++) {

                response = Tools.Internal_MethodUrl(
                        url,
                        Constants.DEFAULT_HTTP_TIMEOUT  + (Constants.DEFAULT_HTTP_EXTRA_TIMEOUT * i),
                        null,
                        null,
                        0,
                        new String[0][0]
                );

                if (response != null) {

                    if (response.status == 200) {

                        JsonObject obj = parseString(response.responseText).getAsJsonObject();

                        if (obj.isJsonObject() && !obj.get("hosts").isJsonNull()) {

                            return ProcessHostArray(obj.get("hosts").getAsJsonArray());
                        }

                        break;
                    }

                }
            }

        } catch (Exception e) {
            Log.w(TAG, "GetHostContent e " , e);
        }

        return null;

    }

    private static List<ChannelContentObj> GetLiveContent(String url, String object, String object2, boolean sort, int screen)  {

        try {
            Tools.ResponseObj response;

            for (int i = 0; i < 3; i++) {

                response = Tools.Internal_MethodUrl(
                        url,
                        Constants.DEFAULT_HTTP_TIMEOUT  + (Constants.DEFAULT_HTTP_EXTRA_TIMEOUT * i),
                        null,
                        null,
                        0,
                        Constants.DEFAULT_HEADERS
                );

                if (response != null) {

                    if (response.status == 200) {

                        JsonObject obj = parseString(response.responseText).getAsJsonObject();

                        if (obj.isJsonObject() && !obj.get(object).isJsonNull()) {

                            return ProcessLiveArray(
                                    obj.get(object).getAsJsonArray(),//Get the follows array
                                    object2,
                                    sort,
                                    screen
                            );
                        }

                        break;
                    }

                }
            }

        } catch (Exception e) {
            Log.w(TAG, "GetLiveContent e " , e);
        }

        return null;

    }

    private static List<ChannelContentObj> ProcessHostArray(JsonArray Streams)  {
        List<ChannelContentObj> content = new ArrayList<>();
        Set<String> TempArray = new HashSet<>();

        JsonObject obj;
        JsonObject objTarget;
        JsonObject objChannel;
        JsonObject objPreview;

        String channelId;
        String description;

        int viewers;
        int objSize = Streams.size();

        DecimalFormat decimalFormat = getDecimalFormat();

        if (objSize < 1) return null;
        else content.add(getRefreshContent());

        for (int j = 0; j < objSize; j++) {

            obj = Streams.get(j).getAsJsonObject();//Get the position in the follows array

            if (obj.isJsonObject() && !obj.get("target").isJsonNull()) {
                objTarget = obj.get("target").getAsJsonObject(); //Get the channel obj in position

                channelId = objTarget.get("_id").getAsString();

                if (!TempArray.contains(channelId) && !obj.get("display_name").isJsonNull()) {//Prevent add duplicated

                    TempArray.add(channelId);

                    objPreview = !objTarget.get("preview_urls").isJsonNull() ? objTarget.get("preview_urls").getAsJsonObject() : null;

                    description = !objTarget.get("meta_game").isJsonNull() ? objTarget.get("meta_game").getAsString() : "";
                    if (!Objects.equals(description, "")) description = "Playing " + description + ", for ";

                    viewers = !objTarget.get("viewers").isJsonNull() ? objTarget.get("viewers").getAsInt() : 0;

                    objChannel = !objTarget.get("channel").isJsonNull() ? objTarget.get("channel").getAsJsonObject() : null;

                    if (objChannel != null && !objChannel.get("display_name").isJsonNull()) {

                        content.add(
                                new ChannelContentObj(
                                        obj.get("display_name").getAsString() + " hosting " + objChannel.get("display_name").getAsString(),
                                        description + decimalFormat.format(viewers) + " viewers\n" + (!objTarget.get("title").isJsonNull() ? objTarget.get("title").getAsString() : ""),
                                        objPreview != null && !objPreview.get("large").isJsonNull() ? objPreview.get("large").getAsString() : Constants.VIDEO_404,
                                        TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9,
                                        viewers,
                                        new Gson().toJson(new PreviewObj(obj, "HOST", Constants.CHANNEL_TYPE_USER_HOST)),
                                        true
                                )
                        );

                    }
                }

            }
        }

        int contentSize = content.size();

        if (contentSize > 1) {
            Collections.sort(
                    content.subList(1, contentSize),
                    compareViewers
            );
        }

        return contentSize > 0 ? content : null;
    }

    private static List<ChannelContentObj> ProcessLiveArray(JsonArray Streams, String object2, boolean sort, int screen)  {
        List<ChannelContentObj> content = new ArrayList<>();

        int objSize = Streams.size();
        if (objSize < 1) return null;
        else content.add(getRefreshContent());

        Set<String> TempArray = new HashSet<>();

        JsonObject obj;
        JsonObject objChannel;
        JsonObject objPreview;

        String channelId;
        String description;
        String game;
        String StreamCreated_at;

        boolean emptyGame;

        int contentSize = 0;
        int viewers;

        DecimalFormat decimalFormat = getDecimalFormat();
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        input.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date;
        long timeMsNow = new Date().getTime();

        try {

            for (int i = 0; i < objSize; i++) {

                obj = Streams.get(i).getAsJsonObject();//Get the position in the follows array

                if (object2 != null) {
                    obj = obj.get(object2).getAsJsonObject();//Featured holds the featured stream inside another level
                }

                if (obj.isJsonObject() && !obj.get("channel").isJsonNull()) {
                    objChannel = obj.get("channel").getAsJsonObject(); //Get the channel obj in position

                    channelId = objChannel.get("_id").getAsString();

                    if (!TempArray.contains(channelId) && //Prevent add duplicated
                            !objChannel.get("display_name").isJsonNull()) {

                        TempArray.add(channelId);

                        objPreview = !obj.get("preview").isJsonNull() ? obj.get("preview").getAsJsonObject() : null;
                        game = !obj.get("game").isJsonNull() ? obj.get("game").getAsString() : "";
                        viewers = !obj.get("viewers").isJsonNull() ? obj.get("viewers").getAsInt() : 0;
                        StreamCreated_at = !obj.get("created_at").isJsonNull() ? obj.get("created_at").getAsString() : null;

                        if (StreamCreated_at != null) {

                            date = input.parse(StreamCreated_at);

                            if (date != null) {

                                StreamCreated_at = getTimeFromMs(timeMsNow - date.getTime());

                            }
                        }

                        emptyGame = Objects.equals(game, "");

                        description = String.format(Locale.US,
                                "%s %s %s for %s viewers\n%s",
                                emptyGame ? "Streaming" : "Playing",
                                game + (emptyGame ? "" : ","),
                                StreamCreated_at != null ? StreamCreated_at : "",
                                decimalFormat.format(viewers),
                                !objChannel.get("status").isJsonNull() ? objChannel.get("status").getAsString() : ""
                        );

                        content.add(
                                new ChannelContentObj(
                                        objChannel.get("display_name").getAsString(),
                                        description,
                                        objPreview != null && !objPreview.get("large").isJsonNull() ? objPreview.get("large").getAsString() : Constants.VIDEO_404,
                                        TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9,
                                        viewers,
                                        new Gson().toJson(new PreviewObj(obj, "LIVE", screen)),
                                        !obj.get("broadcast_platform").isJsonNull() && (obj.get("broadcast_platform").getAsString()).contains("live")
                                )
                        );
                    }

                }
            }

            contentSize = content.size();

            if (sort && contentSize > 1) {
                Collections.sort(
                        content.subList(1, contentSize),
                        compareViewers
                );
            }

        } catch (Exception e) {
            Log.w(TAG, "ProcessLiveArray e " + e.getMessage());
        }

        return contentSize > 0 ? content : null;
    }

    private static List<ChannelContentObj> GetGamesContent(String url, String object, String[][] HEADERS, int screen)  {

        JsonArray Games = GetLiveGames(url, object, HEADERS);//Get the Games array
        List<ChannelContentObj> content = new ArrayList<>();

        int objSize = Games != null ? Games.size() : 0;

        if (objSize > 0) content.add(getRefreshContent());
        else return null;

        JsonObject obj;
        JsonObject objGame;
        JsonObject objPreview;

        int viewers;
        int channels;

        DecimalFormat decimalFormat = getDecimalFormat();

        try {

            for (int i = 0; i < objSize; i++) {

                obj = Games.get(i).getAsJsonObject();
                objGame = obj.get("game").getAsJsonObject(); //Get the channel obj in position

                if (!objGame.get("name").isJsonNull()) {

                    viewers = !obj.get("viewers").isJsonNull() ? obj.get("viewers").getAsInt() : 0;
                    channels = !obj.get("channels").isJsonNull() ? obj.get("channels").getAsInt() : 0;
                    objPreview = !objGame.get("box").isJsonNull() ? objGame.get("box").getAsJsonObject() : null;

                    content.add(
                            new ChannelContentObj(
                                    objGame.get("name").getAsString(),
                                    decimalFormat.format(channels) + " Channels\nFor " + decimalFormat.format(viewers) + " viewers",
                                    objPreview != null && !objPreview.get("large").isJsonNull() ? objPreview.get("large").getAsString() : Constants.GAME_404,
                                    TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3,
                                    viewers,
                                    new Gson().toJson(new PreviewObj(objGame, "GAME", screen)),
                                    false
                            )
                    );

                }

            }

            if (content.size() > 0) return content;

        } catch(Exception e){
            Log.w(TAG, "GetGamesContent e ", e);
        }

        return null;

    }

    public static JsonArray GetLiveGames(String url, String object, String[][] HEADERS)  {
        JsonArray Result = new JsonArray();
        int status = 0;

        try {
            Set<String> TempArray = new HashSet<>();

            Tools.ResponseObj response;
            JsonObject obj;
            JsonObject objGame;
            JsonArray Games;
            int objSize;

            String gameId;

            for (int i = 0; i < 3; i++) {

                response = Tools.Internal_MethodUrl(
                        url,
                        Constants.DEFAULT_HTTP_TIMEOUT  + (Constants.DEFAULT_HTTP_EXTRA_TIMEOUT * i),
                        null,
                        null,
                        0,
                        HEADERS
                );

                if (response != null) {
                    status = response.status;

                    if (status == 200) {

                        obj = parseString(response.responseText).getAsJsonObject();

                        if (obj.isJsonObject() && !obj.get(object).isJsonNull()) {

                            Games = obj.get(object).getAsJsonArray();//Get the Games array
                            objSize = Games.size();

                            if (objSize < 1) return Result;

                            for (int j = 0; j < objSize; j++) {

                                obj = Games.get(j).getAsJsonObject();

                                if (obj.isJsonObject() && !obj.get("game").isJsonNull()) {

                                    objGame = obj.get("game").getAsJsonObject();//Get the game obj in position
                                    gameId = !objGame.get("_id").isJsonNull() ? objGame.get("_id").getAsString() : null;

                                    if (gameId != null && !TempArray.contains(gameId)) {//Prevent add duplicated

                                        TempArray.add(gameId);
                                        Result.add(obj);

                                    }

                                }
                            }

                        }
                        break;
                    }

                }
            }

        } catch (Exception e) {
            Log.w(TAG, "GetLiveGames e ", e);
        }

        return status == 200 ? Result : null;

    }

}
