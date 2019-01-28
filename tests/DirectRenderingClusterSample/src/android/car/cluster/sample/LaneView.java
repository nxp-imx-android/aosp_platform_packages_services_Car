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
package android.car.cluster.sample;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.car.cluster.navigation.Lane;
import androidx.car.cluster.navigation.LaneDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * View component that displays the Lane preview information on the instrument cluster display
 */
public class LaneView extends LinearLayout {
    private ArrayList<Lane> mLanes;

    private final int mWidth = (int) getResources().getDimension(R.dimen.lane_width);
    private final int mHeight = (int) getResources().getDimension(R.dimen.lane_height);

    public LaneView(Context context) {
        super(context);
    }

    public LaneView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LaneView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setLanes(List<Lane> lanes) {
        mLanes = new ArrayList<>(lanes);
        removeAllViews();

        for (Lane lane : mLanes) {
            Bitmap bitmap = combineBitmapFromLane(lane);
            ImageView imgView = new ImageView(getContext());
            imgView.setImageBitmap(bitmap);
            imgView.setAdjustViewBounds(true);
            addView(imgView);
        }
    }

    private Bitmap combineBitmapFromLane(Lane lane) {
        if (lane.getDirections().isEmpty()) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        for (LaneDirection laneDir : lane.getDirections()) {
            if (!laneDir.isHighlighted()) {
                drawToCanvas(laneDir, canvas, false);
            }
        }

        for (LaneDirection laneDir : lane.getDirections()) {
            if (laneDir.isHighlighted()) {
                drawToCanvas(laneDir, canvas, true);
            }
        }

        return bitmap;
    }


    private void drawToCanvas(LaneDirection laneDir, Canvas canvas, boolean isHighlighted) {
        VectorDrawable icon = (VectorDrawable) getLaneIcon(laneDir);
        icon.setBounds(0, 0, mWidth, mHeight);
        icon.setColorFilter(new PorterDuffColorFilter(isHighlighted
                ? getContext().getColor(R.color.laneDirectionHighlighted)
                : getContext().getColor(R.color.laneDirection),
                PorterDuff.Mode.SRC_ATOP));
        icon.draw(canvas);
    }

    private Drawable getLaneIcon(@Nullable LaneDirection laneDir) {
        if (laneDir == null) {
            return null;
        }
        switch (laneDir.getShape()) {
            case UNKNOWN:
                return null;
            case STRAIGHT:
                return mContext.getDrawable(R.drawable.direction_continue);
            case SLIGHT_LEFT:
                return mContext.getDrawable(R.drawable.direction_turn_slight_left);
            case SLIGHT_RIGHT:
                return mContext.getDrawable(R.drawable.direction_turn_slight_right);
            case NORMAL_LEFT:
                return mContext.getDrawable(R.drawable.direction_turn_left);
            case NORMAL_RIGHT:
                return mContext.getDrawable(R.drawable.direction_turn_right);
            case SHARP_LEFT:
                return mContext.getDrawable(R.drawable.direction_turn_sharp_left);
            case SHARP_RIGHT:
                return mContext.getDrawable(R.drawable.direction_turn_sharp_right);
            case U_TURN_LEFT:
                return mContext.getDrawable(R.drawable.direction_uturn);
            case U_TURN_RIGHT:
                return mContext.getDrawable(R.drawable.direction_uturn_right);
        }
        return null;
    }
}
