package com.kiminonawa.mydiary.entries.diary.area;

import android.graphics.RectF;
import android.net.Uri;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.common.RotationOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;

/**
 * Loads a photo into a Fresco picture showing only the visible area the person chose (or the center).
 */
public final class AreaImage {

    //Never ask Fresco for more than this on one side
    private static final int MAX_SIDE = 2400;

    private AreaImage() {
    }

    /**
     * @param view   the picture
     * @param uri    the photo file
     * @param area   the part to see (fractions of the photo), or null for the center
     * @param width  size (pixels) of the picture on the screen
     * @param height size (pixels) of the picture on the screen
     */
    public static void show(SimpleDraweeView view, Uri uri, RectF area, int width, int height) {
        float widthFraction = area == null ? 1f : area.width();
        float heightFraction = area == null ? 1f : area.height();
        //A small part of the photo has to be read with more detail
        int requestWidth = Math.min(MAX_SIDE, Math.round(width / Math.max(widthFraction, 0.05f)));
        int requestHeight = Math.min(MAX_SIDE, Math.round(height / Math.max(heightFraction, 0.05f)));
        ImageRequest request = ImageRequestBuilder.newBuilderWithSource(uri)
                .setResizeOptions(new ResizeOptions(Math.max(requestWidth, 1), Math.max(requestHeight, 1)))
                .setRotationOptions(RotationOptions.autoRotate())
                .build();
        view.getHierarchy().setActualImageScaleType(
                area == null ? ScalingUtils.ScaleType.CENTER_CROP : new AreaScaleType(area));
        view.setController(Fresco.newDraweeControllerBuilder()
                .setOldController(view.getController())
                .setImageRequest(request)
                .build());
    }
}
