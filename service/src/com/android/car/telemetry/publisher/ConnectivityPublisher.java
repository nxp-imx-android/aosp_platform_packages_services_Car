/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.telemetry.publisher;

import static com.android.car.telemetry.CarTelemetryService.DEBUG;

import android.annotation.NonNull;
import android.content.Context;
import android.net.INetworkStatsService;
import android.net.NetworkStats;
import android.net.NetworkTemplate;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArrayMap;

import com.android.car.CarLog;
import com.android.car.telemetry.TelemetryProto;
import com.android.car.telemetry.TelemetryProto.ConnectivityPublisher.OemType;
import com.android.car.telemetry.TelemetryProto.ConnectivityPublisher.Transport;
import com.android.car.telemetry.TelemetryProto.Publisher.PublisherCase;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.internal.util.Preconditions;
import com.android.server.utils.Slogf;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Publisher implementation for {@link TelemetryProto.ConnectivityPublisher}.
 *
 * <p>This publisher pulls netstats periodically from NetworkStatsService. It publishes statistics
 * between the last pull and now. The {@link NetworkStats} are stored in NetworkStatsService in
 * 2 hours buckets, we won't be able to get precise netstats if we use the buckets mechanism,
 * that's why we will be storing baseline (or previous) netstats in ConnectivityPublisher to find
 * netstats diff between now and the last pull.
 */
public class ConnectivityPublisher extends AbstractPublisher {
    // The default bucket duration used when query a snapshot from NetworkStatsService. The value
    // should be sync with NetworkStatsService#DefaultNetworkStatsSettings#getUidConfig.
    private static final long NETSTATS_UID_DEFAULT_BUCKET_DURATION_MILLIS =
            Duration.ofHours(2).toMillis();

    // TODO(b/202115033): Flatten the load spike by pulling reports for each MetricsConfigs
    //                    using separate periodical timers.
    // TODO(b/197905656): Use driving sessions to compute network usage statistics.
    private static final Duration PULL_NETSTATS_PERIOD = Duration.ofSeconds(60);

    // Assign the method to {@link Runnable}, otherwise the handler fails to remove it.
    private final Runnable mPullNetstatsPeriodically = this::pullNetstatsPeriodically;

    private final Context mContext;

    // Use ArrayMap instead of HashMap to improve memory usage. It doesn't store more than 100s
    // of items, and it's good enough performance-wise.
    private final ArrayMap<QueryParam, ArrayList<DataSubscriber>> mSubscribers = new ArrayMap<>();

    // Stores previous netstats for computing network usage since the last pull.
    private final ArrayMap<QueryParam, NetworkStats> mTransportPreviousNetstats = new ArrayMap<>();

    // All the methods in this class are expected to be called on this handler's thread.
    private final Handler mTelemetryHandler;

    private INetworkStatsService mNetworkStatsService;
    private boolean mIsPullingNetstats = false;

    ConnectivityPublisher(
            @NonNull PublisherFailureListener failureListener,
            @NonNull INetworkStatsService networkStatsService,
            @NonNull Handler telemetryHandler,
            @NonNull Context context) {
        super(failureListener);
        mNetworkStatsService = networkStatsService;
        mTelemetryHandler = telemetryHandler;
        mContext = context;
        for (Transport transport : Transport.values()) {
            if (transport.equals(Transport.TRANSPORT_UNDEFINED)) {
                continue;
            }
            for (OemType oemType : OemType.values()) {
                if (oemType.equals(OemType.OEM_UNDEFINED)) {
                    continue;
                }
                mSubscribers.put(QueryParam.build(transport, oemType), new ArrayList<>());
            }
        }
        // Pull baseine netstats to compute statistics for all the QueryParam combinations since
        // boot. It's needed to calculate netstats since the boot.
        mTelemetryHandler.post(this::pullInitialNetstats);
    }

    @Override
    public void addDataSubscriber(@NonNull DataSubscriber subscriber) {
        TelemetryProto.Publisher publisherParam = subscriber.getPublisherParam();
        Preconditions.checkArgument(
                publisherParam.getPublisherCase() == PublisherCase.CONNECTIVITY,
                "Subscribers only with ConnectivityPublisher are supported by this class.");

        mSubscribers.get(QueryParam.forSubscriber(subscriber)).add(subscriber);
        if (!mIsPullingNetstats) {
            if (DEBUG) {
                Slogf.d(CarLog.TAG_TELEMETRY, "NetStats will be pulled in " + PULL_NETSTATS_PERIOD);
            }
            mIsPullingNetstats = true;
            mTelemetryHandler.postDelayed(
                    mPullNetstatsPeriodically, PULL_NETSTATS_PERIOD.toMillis());
        }
    }

    @Override
    public void removeDataSubscriber(@NonNull DataSubscriber subscriber) {
        mSubscribers.get(QueryParam.forSubscriber(subscriber)).remove(subscriber);
        if (isSubscribersEmpty()) {
            mIsPullingNetstats = false;
            mTelemetryHandler.removeCallbacks(mPullNetstatsPeriodically);
        }
    }

    @Override
    public void removeAllDataSubscribers() {
        for (int i = 0; i < mSubscribers.size(); i++) {
            mSubscribers.valueAt(i).clear();
        }
        mIsPullingNetstats = false;
        mTelemetryHandler.removeCallbacks(mPullNetstatsPeriodically);
    }

    @Override
    public boolean hasDataSubscriber(@NonNull DataSubscriber subscriber) {
        return mSubscribers.get(QueryParam.forSubscriber(subscriber)).contains(subscriber);
    }

    private void pullInitialNetstats() {
        if (DEBUG) {
            Slogf.d(CarLog.TAG_TELEMETRY, "ConnectivityPublisher is pulling initial netstats");
        }
        try {
            for (QueryParam param : mSubscribers.keySet()) {
                mTransportPreviousNetstats.put(param, getSummaryForAllUid(param));
            }
        } catch (RemoteException e) {
            // Can't do much if the NetworkStatsService is not available. Next netstats pull
            // will update the baseline netstats.
            Slogf.w(CarLog.TAG_TELEMETRY, e);
        }
    }

    private void pullNetstatsPeriodically() {
        if (!mIsPullingNetstats) {
            return;
        }

        for (int i = 0; i < mSubscribers.size(); i++) {
            ArrayList<DataSubscriber> subscribers = mSubscribers.valueAt(i);
            if (subscribers.isEmpty()) {
                continue;
            }
            pullAndForwardNetstatsToSubscribers(mSubscribers.keyAt(i), subscribers);
        }

        if (DEBUG) {
            Slogf.d(CarLog.TAG_TELEMETRY, "NetStats will be pulled in " + PULL_NETSTATS_PERIOD);
        }
        mTelemetryHandler.postDelayed(mPullNetstatsPeriodically, PULL_NETSTATS_PERIOD.toMillis());
    }

    private void pullAndForwardNetstatsToSubscribers(
            @NonNull QueryParam param, @NonNull ArrayList<DataSubscriber> subscribers) {
        NetworkStats current;
        long collectedAtMillis = System.currentTimeMillis();
        try {
            current = getSummaryForAllUid(param);
        } catch (RemoteException | NullPointerException e) {
            // If the NetworkStatsService is not available, it retries in the next pull.
            Slogf.w(CarLog.TAG_TELEMETRY, e);
            return;
        }
        NetworkStats baseline = mTransportPreviousNetstats.get(param);
        // Update the baseline, so that next pull will calculate diff from the current.
        mTransportPreviousNetstats.put(param, current);
        if (baseline == null) {
            Slogf.w(
                    CarLog.TAG_TELEMETRY,
                    "Baseline is null for param %s. Will try again in the next pull.",
                    param);
            return;
        }

        NetworkStats diff = current.subtract(baseline); // network usage since last pull
        PersistableBundle data = convertStatsToBundle(diff, collectedAtMillis);

        for (DataSubscriber subscriber : subscribers) {
            subscriber.push(data);
        }
    }

    /**
     * Converts the provided {@link NetworkStats} object to {@link PersistableBundle}. It also
     * combines dimensions {@code set}, {@code metered}, {@code roaming} and {@code defaultNetwork},
     * leaving only {@code uid} and {@code tag}.
     *
     * <p>Format: <code>
     *   PersistableBundle[{
     *     collectedAtMillis = 1640779280000,
     *     durationMillis = 60_000,
     *     size  = 3,
     *     uid = IntArray[0, 1000, 12345],
     *     tag = IntArray[0, 0, 5555],
     *     rxBytes = LongArray[0, 0, 0],
     *     txBytes = LongArray[0, 0, 0],
     *   }]
     * </code> Where "collectedAtMillis" is a timestamp (wall clock) since epoch when data is
     * collected; field "durationMillis" is a time range when the data was collected; field "size"
     * is the length of "uid", "tag", "rxBytes", "txBytes" arrays; fields "uid" and "tag" are
     * dimensions; fields "rxBytes", "txBytes" are received and transmitted bytes for given
     * dimensions.
     *
     * <p>"tag" field may contain "0" {@link NetworkStats.TAG_NONE}, which is the total value across
     * all the tags.
     *
     * <p>This code is similar to hidden {@code NetworkStats#groupedByIface()} method.
     */
    private static PersistableBundle convertStatsToBundle(
            NetworkStats stats, long collectedAtMillis) {
        int dataSize = stats.size();
        int[] uid = new int[dataSize];
        int[] tag = new int[dataSize];
        long[] rxBytes = new long[dataSize];
        long[] txBytes = new long[dataSize];
        int aggregatedDataSize = 0;
        for (int rawIndex = 0; rawIndex < dataSize; rawIndex++) {
            NetworkStats.Entry raw = stats.getValues(rawIndex, null);
            int index = -1;
            for (int j = 0; j < aggregatedDataSize; j++) {
                if (uid[j] == raw.uid && tag[j] == raw.tag) {
                    index = j;
                    break;
                }
            }
            if (index == -1) {
                aggregatedDataSize += 1; // append the new Entry
                index = aggregatedDataSize - 1;
                uid[index] = raw.uid;
                tag[index] = raw.tag;
            }
            rxBytes[index] += raw.rxBytes;
            txBytes[index] += raw.txBytes;
        }

        PersistableBundle data = new PersistableBundle();
        data.putLong("collectedAtMillis", collectedAtMillis);
        data.putLong("durationMillis", stats.getElapsedRealtime());
        data.putInt("size", aggregatedDataSize);
        data.putIntArray("uid", Arrays.copyOf(uid, aggregatedDataSize));
        data.putIntArray("tag", Arrays.copyOf(tag, aggregatedDataSize));
        data.putLongArray("rxBytes", Arrays.copyOf(rxBytes, aggregatedDataSize));
        data.putLongArray("txBytes", Arrays.copyOf(txBytes, aggregatedDataSize));
        return data;
    }

    /**
     * Creates a snapshot of NetworkStats since boot for the given QueryParam, but adds 1 bucket
     * duration before boot as a buffer to ensure at least one full bucket will be included. Note
     * that this should be only used to calculate diff since the snapshot might contains some
     * traffic before boot.
     *
     * <p>This method might block the thread for several seconds.
     *
     * <p>TODO(b/197905656): run this method on a separate thread for better performance.
     */
    @NonNull
    private NetworkStats getSummaryForAllUid(@NonNull QueryParam param) throws RemoteException {
        long currentTimeInMillis = System.currentTimeMillis();
        long elapsedMillisSinceBoot = SystemClock.elapsedRealtime(); // including sleep

        // TODO(b/197905656): consider using the current netstats bucket value
        //                    from Settings.Global.NETSTATS_UID_BUCKET_DURATION.
        return getSummaryForAllUidInternal(
                param,
                /* start= */ currentTimeInMillis
                        - elapsedMillisSinceBoot
                        - NETSTATS_UID_DEFAULT_BUCKET_DURATION_MILLIS,
                /* end= */ currentTimeInMillis,
                mContext.getOpPackageName());
    }

    private NetworkStats getSummaryForAllUidInternal(
            QueryParam param, long startMillis, long endMillis, String callingPackage)
            throws RemoteException {
        // No need to close the session, it just resets the internal state.
        return mNetworkStatsService
                .openSessionForUsageStats(/* flags= */ 0, callingPackage)
                .getSummaryForAllUid(
                        buildNetworkTemplate(param),
                        startMillis,
                        endMillis,
                        /* includeTags= */ true);
    }

    private static NetworkTemplate buildNetworkTemplate(QueryParam param) {
        return new NetworkTemplate(
                param.getMatchRule(),
                /* subscriberId= */ null,
                /* matchSubscriberIds= */ null,
                /* networkId= */ null,
                NetworkStats.METERED_ALL,
                NetworkStats.ROAMING_ALL,
                NetworkStats.DEFAULT_NETWORK_ALL,
                NetworkTemplate.NETWORK_TYPE_ALL,
                param.getOemManaged());
    }

    private boolean isSubscribersEmpty() {
        for (int i = 0; i < mSubscribers.size(); i++) {
            if (!mSubscribers.valueAt(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parameters to query data from NetworkStatsService. Converts {@link Transport} and
     * {@link OemType} values into NetworkStatsService supported values.
     */
    private static class QueryParam {
        private int mMatchRule;
        private int mOemManaged;

        @NonNull
        static QueryParam forSubscriber(@NonNull DataSubscriber subscriber) {
            TelemetryProto.ConnectivityPublisher publisher =
                    subscriber.getPublisherParam().getConnectivity();
            return build(publisher.getTransport(), publisher.getOemType());
        }

        @NonNull
        static QueryParam build(@NonNull Transport transport, @NonNull OemType oemType) {
            return new QueryParam(
                    getNetworkTemplateMatch(transport), getNetstatsOemManaged(oemType));
        }

        private QueryParam(int matchRule, int oemManaged) {
            mMatchRule = matchRule;
            mOemManaged = oemManaged;
        }

        int getMatchRule() {
            return mMatchRule;
        }

        int getOemManaged() {
            return mOemManaged;
        }

        private static int getNetworkTemplateMatch(@NonNull Transport transport) {
            switch (transport) {
                case TRANSPORT_CELLULAR:
                    return NetworkTemplate.MATCH_MOBILE_WILDCARD;
                case TRANSPORT_ETHERNET:
                    return NetworkTemplate.MATCH_ETHERNET;
                case TRANSPORT_WIFI:
                    return NetworkTemplate.MATCH_WIFI_WILDCARD;
                case TRANSPORT_BLUETOOTH:
                    return NetworkTemplate.MATCH_BLUETOOTH;
                default:
                    throw new IllegalArgumentException("Unexpected transport " + transport.name());
            }
        }

        private static int getNetstatsOemManaged(@NonNull OemType oemType) {
            switch (oemType) {
                case OEM_NONE:
                    return NetworkTemplate.OEM_MANAGED_NO;
                case OEM_MANAGED:
                    return NetworkTemplate.OEM_MANAGED_YES;
                default:
                    throw new IllegalArgumentException("Unexpected oem_type " + oemType.name());
            }
        }

        @Override
        public String toString() {
            return "QueryParam(matchRule=" + mMatchRule + ", oemManaged=" + mOemManaged + ")";
        }

        @Override
        public int hashCode() {
            return Objects.hash(mMatchRule, mOemManaged);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof QueryParam)) {
                return false;
            }
            QueryParam other = (QueryParam) obj;
            return mMatchRule == other.mMatchRule && mOemManaged == other.mOemManaged;
        }
    }
}
