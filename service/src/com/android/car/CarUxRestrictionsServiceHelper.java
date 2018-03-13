/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car;

import static com.android.car.CarUxRestrictionsManagerService.UX_RESTRICTIONS_UNKNOWN;

import android.annotation.XmlRes;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateEvent.CarDrivingState;
import android.car.drivingstate.CarUxRestrictions.CarUxRestrictionsInfo;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParserException;

/**
 * Helper class to {@link CarUxRestrictionsManagerService} and it takes care of the foll:
 * <ol>
 *  <li>Parses the given XML resource and builds a hashmap to store the driving state to UX
 * restrictions mapping information provided in the XML.</li>
 *  <li>Finds the UX restrictions for the given driving state and speed from the data structure it
 * built above.</li>
 * </ol>
 */
/* package */ class CarUxRestrictionsServiceHelper {
    private static final String TAG = "UxRServiceHelper";
    // XML tags to parse
    private static final String ROOT_ELEMENT = "UxRestrictions";
    private static final String RESTRICTION_MAPPING = "RestrictionMapping";
    private static final String RESTRICTION_PARAMETERS = "RestrictionParameters";
    private static final String DRIVING_STATE = "DrivingState";
    private static final String RESTRICTIONS = "Restrictions";
    /* Hashmap that maps driving state to RestrictionsInfo.
    RestrictionsInfo maintains a list of RestrictionsPerSpeedRange.
    The list size will be one for Parked and Idling states, but could be more than one
    for Moving state, if moving state supports multiple speed ranges.*/
    private Map<Integer, RestrictionsInfo> mRestrictionsMap = new HashMap<>();
    private final Context mContext;
    @XmlRes private final int mXmlResource;

    public CarUxRestrictionsServiceHelper(Context context, @XmlRes int xmlRes) {
        mContext = context;
        mXmlResource = xmlRes;
    }

    /**
     * Loads the UX restrictions related information from the XML resource.
     *
     * @return true if successful, false if the XML is malformed
     */
    public boolean loadUxRestrictionsFromXml() throws IOException, XmlPullParserException {
        mRestrictionsMap.clear();
        XmlResourceParser parser = mContext.getResources().getXml(mXmlResource);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        int type;
        // Traverse till we get to the first tag
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && type != XmlResourceParser.START_TAG) {
        }
        if (!ROOT_ELEMENT.equals(parser.getName())) {
            Log.e(TAG, "XML root element invalid: " + parser.getName());
            return false;
        }

        // Traverse till the end and every time we hit a start tag, check for the type of the tag
        // and load the corresponding information.
        while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
            if (parser.next() == XmlResourceParser.START_TAG) {
                switch (parser.getName()) {
                    case RESTRICTION_MAPPING:
                        if (!mapDrivingStateToRestrictions(parser, attrs)) {
                            return false;
                        }
                        break;
                    case RESTRICTION_PARAMETERS:
                        //TODO(b/72155508)
                        break;
                    default:
                        Log.w(TAG, "Unknown class:" + parser.getName());
                        continue;
                }
            }
        }
        return true;
    }

    /**
     * Parses the information in the <restrictionMapping> tag to construct the mapping from
     * driving state to UX restrictions.
     */
    private boolean mapDrivingStateToRestrictions(XmlResourceParser parser, AttributeSet attrs)
            throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Invalid arguments");
            }
            return false;
        }
        // The parser should be at the <restrictionMapping> tag at this point.
        if (!RESTRICTION_MAPPING.equals(parser.getName())) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Parser not at restrictionMapping element: " + parser.getName());
            }
            return false;
        }
        if (!traverseToTag(parser, DRIVING_STATE)) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "No <" + DRIVING_STATE + "> tag in XML");
            }
            return false;
        }
        // Handle all the <drivingState> tags.
        while (DRIVING_STATE.equals(parser.getName())) {
            if (parser.getEventType() == XmlResourceParser.START_TAG) {
                // 1. Get the driving state attributes: driving state and speed range
                TypedArray a = mContext.getResources().obtainAttributes(attrs,
                        R.styleable.UxRestrictions_DrivingState);
                int drivingState = a
                        .getInt(R.styleable.UxRestrictions_DrivingState_state,
                                CarDrivingStateEvent.DRIVING_STATE_UNKNOWN);
                float minSpeed = a
                        .getFloat(
                                R.styleable
                                        .UxRestrictions_DrivingState_minSpeed,
                                RestrictionsPerSpeedRange.SPEED_INVALID);
                float maxSpeed = a
                        .getFloat(
                                R.styleable
                                        .UxRestrictions_DrivingState_maxSpeed,
                                RestrictionsPerSpeedRange.SPEED_INVALID);
                a.recycle();

                // 2. Traverse to the <restrictions> tag
                if (!traverseToTag(parser, RESTRICTIONS)) {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                        Log.e(TAG, "No <" + RESTRICTIONS + "> tag in XML");
                    }
                    return false;
                }

                // 3. Parse the restrictions for this driving state
                int restrictions = parseRestrictions(parser, attrs);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Map " + drivingState + " : " + restrictions);
                }

                // Update the hashmap if the driving state and restrictions info are valid.
                if (drivingState != CarDrivingStateEvent.DRIVING_STATE_UNKNOWN
                        && restrictions != UX_RESTRICTIONS_UNKNOWN) {
                    addToRestrictionsMap(drivingState, minSpeed, maxSpeed, restrictions);
                }
            }
            parser.next();
        }
        return true;
    }

    /**
     * Parses the <restrictions> tag nested with the <drivingState>.  This provides the restrictions
     * for the enclosing driving state.
     */
    private int parseRestrictions(XmlResourceParser parser, AttributeSet attrs)
            throws IOException, XmlPullParserException {
        int restrictions = UX_RESTRICTIONS_UNKNOWN;
        if (parser == null || attrs == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Invalid Arguments");
            }
            return restrictions;
        }

        while (RESTRICTIONS.equals(parser.getName())
                && parser.getEventType() == XmlResourceParser.START_TAG) {
            TypedArray a = mContext.getResources().obtainAttributes(attrs,
                    R.styleable.UxRestrictions_Restrictions);
            restrictions = a.getInt(
                    R.styleable.UxRestrictions_Restrictions_uxr,
                    -1);
            a.recycle();
            parser.next();
        }
        return restrictions;
    }

    private void addToRestrictionsMap(int drivingState, float minSpeed, float maxSpeed,
            int restrictions) {
        RestrictionsPerSpeedRange res = new RestrictionsPerSpeedRange(minSpeed, maxSpeed,
                restrictions);
        RestrictionsInfo restrictionsList = mRestrictionsMap.get(drivingState);
        if (restrictionsList == null) {
            restrictionsList = new RestrictionsInfo();
        }
        restrictionsList.addRestrictions(res);
        mRestrictionsMap.put(drivingState, restrictionsList);
    }

    private boolean traverseToTag(XmlResourceParser parser, String tag)
            throws IOException, XmlPullParserException {
        if (tag == null || parser == null) {
            return false;
        }
        int type;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (type == XmlResourceParser.START_TAG && parser.getName().equals(tag)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Dump the driving state to UX restrictions mapping.
     */
    public void dump(PrintWriter writer) {
        for (Integer state : mRestrictionsMap.keySet()) {
            RestrictionsInfo list = mRestrictionsMap.get(state);
            writer.println("===========================================");
            writer.println("Driving State to UXR");
            if (list != null && list.mRestrictionsList != null) {
                writer.println("State:" + getDrivingStateName(state) + " num restrictions:"
                        + list.mRestrictionsList.size());
                for (RestrictionsPerSpeedRange r : list.mRestrictionsList) {
                    writer.println(
                            "Speed Range: " + r.mMinSpeed + "-" + r.mMaxSpeed + " Restrictions: 0x"
                                    + Integer.toHexString(r.mRestrictions));
                    writer.println("===========================================");
                }
            }

        }
    }

    private static String getDrivingStateName(int state) {
        switch (state) {
            case 0:
                return "parked";
            case 1:
                return "idling";
            case 2:
                return "moving";
            default:
                return "unknown";
        }
    }

    /**
     * Get the UX restrictions for the given driving state and speed.
     *
     * @param drivingState driving state of the vehicle
     * @param currentSpeed speed of the vehicle
     * @return UX restrictions for the given driving state and speed.
     */
    @CarUxRestrictionsInfo
    public int getUxRestrictions(@CarDrivingState int drivingState, float currentSpeed) {
        int restrictions = UX_RESTRICTIONS_UNKNOWN;
        RestrictionsInfo restrictionsList = mRestrictionsMap.get(drivingState);
        // If the XML hasn't been parsed or if the given driving state is not supported in the
        // XML, return error.
        if (restrictionsList == null || restrictionsList.mRestrictionsList == null
                || restrictionsList.mRestrictionsList.isEmpty()) {
            return restrictions;
        }
        // For Parked and Idling, the restrictions list will have only one item, since multiple
        // speed ranges don't make sense in those driving states.
        if (restrictionsList.mRestrictionsList.size() == 1) {
            return restrictionsList.mRestrictionsList.get(0).mRestrictions;
        }
        return restrictionsList.findRestrictions(currentSpeed);
    }


    /**
     * Container for UX restrictions for a speed range.
     * Speed range is valid only for the {@link CarDrivingStateEvent#DRIVING_STATE_MOVING}.
     */
    private class RestrictionsPerSpeedRange {
        public static final int SPEED_INVALID = -1;
        public float mMinSpeed;
        public float mMaxSpeed;
        public int mRestrictions;

        public RestrictionsPerSpeedRange(float minSpeed, float maxSpeed, int restrictions) {
            mMinSpeed = minSpeed;
            mMaxSpeed = maxSpeed;
            mRestrictions = restrictions;
        }

        /**
         * Return if the given speed is in the range of ( {@link #mMinSpeed}, {@link #mMaxSpeed}
         *
         * @param speed Speed to check
         * @return true if in range false if not.
         */
        public boolean includes(float speed) {
            if (mMinSpeed != SPEED_INVALID && mMaxSpeed == SPEED_INVALID) {
                // This is for a range [minSpeed, infinity).  If maxSpeed
                // is invalid and mMinSpeed is a valid, this represents a
                // anything greater than mMinSpeed.
                return speed >= mMinSpeed;
            } else {
                return speed >= mMinSpeed && speed < mMaxSpeed;
            }
        }
    }

    /**
     * Container for a list of {@link RestrictionsPerSpeedRange} per driving state.
     */
    private class RestrictionsInfo {
        private List<RestrictionsPerSpeedRange> mRestrictionsList = new ArrayList<>();

        public void addRestrictions(RestrictionsPerSpeedRange r) {
            mRestrictionsList.add(r);
        }

        /**
         * Find the restrictions for the given speed.  It finds the range that the given speed falls
         * in and gets the restrictions for that speed.
         */
        @CarUxRestrictionsInfo
        public int findRestrictions(float speed) {
            for (RestrictionsPerSpeedRange r : mRestrictionsList) {
                if (r.includes(speed)) {
                    return r.mRestrictions;
                }
            }
            return UX_RESTRICTIONS_UNKNOWN;
        }
    }
}
