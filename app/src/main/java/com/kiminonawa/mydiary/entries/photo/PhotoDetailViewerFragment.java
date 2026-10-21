package com.kiminonawa.mydiary.entries.photo;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.samples.zoomable.DoubleTapGestureListener;
import com.facebook.samples.zoomable.ZoomableDraweeView;
import com.kiminonawa.mydiary.R;

/**
 * Created by daxia on 2017/4/12.
 */

public class PhotoDetailViewerFragment extends Fragment {


    public ZoomableDraweeView zdvPhotoDetail;

    private Uri photoUri;

    public static PhotoDetailViewerFragment newInstance(Uri photoUri) {
        Bundle args = new Bundle();
        PhotoDetailViewerFragment fragment = new PhotoDetailViewerFragment();
        args.putParcelable("photoUri", photoUri);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_diary_photo_detail_viewer, container, false);
        zdvPhotoDetail = (ZoomableDraweeView) view.findViewById(R.id.zdv_photo_detail);
        photoUri = getArguments().getParcelable("photoUri");
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initZoomableDraweeView();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        zdvPhotoDetail = null;
    }

    private void initZoomableDraweeView() {
        zdvPhotoDetail.setAllowTouchInterceptionWhileZoomed(true);
        // needed for double tap to zoom
        zdvPhotoDetail.setIsLongpressEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //Implement the InmmersiveMode
            zdvPhotoDetail.setTapListener(
                    new TapGestureListener(getActivity().getWindow().getDecorView(), zdvPhotoDetail));
        } else {
            //Only implement double tap
            zdvPhotoDetail.setTapListener(
                    new DoubleTapGestureListener(zdvPhotoDetail));
        }
        DraweeController controller = Fresco.newDraweeControllerBuilder()
                .setUri(photoUri)
                .build();
        zdvPhotoDetail.setController(controller);
    }
}

