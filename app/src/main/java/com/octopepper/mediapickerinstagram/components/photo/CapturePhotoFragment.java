package com.octopepper.mediapickerinstagram.components.photo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.octopepper.mediapickerinstagram.R;
import com.octopepper.mediapickerinstagram.commons.cameraview.CameraView;
import com.octopepper.mediapickerinstagram.commons.models.Session;
import com.octopepper.mediapickerinstagram.commons.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class CapturePhotoFragment extends Fragment {

    private static final Interpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();
    private static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();

    @BindView(R.id.mCameraPhotoView)
    CameraView mCameraPhotoView;
    @BindView(R.id.mBtnTakePhoto)
    ImageView mBtnTakePhoto;
    @BindView(R.id.mShutter)
    View mShutter;
    @BindView(R.id.mFlashPhoto)
    ImageView mFlashPhoto;
    @BindView(R.id.mSwitchCamera)
    ImageView mSwitchCamera;

    private static final int[] FLASH_OPTIONS = {
            CameraView.FLASH_AUTO,
            CameraView.FLASH_OFF,
            CameraView.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private int mCurrentFlash;
    private Handler mBackgroundHandler;
    private Session mSession = Session.getInstance();
    private CapturePhotoFragmentListener listener;

    @OnClick(R.id.mBtnTakePhoto)
    void onTakePhotoClick() {
        mCameraPhotoView.takePicture();
        animateShutter();
    }

    @OnClick(R.id.mSwitchCamera)
    void onSwitchCamera() {
        if (mCameraPhotoView != null) {
            int facing = mCameraPhotoView.getFacing();
            mCameraPhotoView.setFacing(facing == CameraView.FACING_FRONT ?
                    CameraView.FACING_BACK : CameraView.FACING_FRONT);
        }
    }

    @OnClick(R.id.mFlashPhoto)
    void onChangeFlashState() {
        if (mCameraPhotoView != null) {
            mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.length;
            mFlashPhoto.setImageResource(FLASH_ICONS[mCurrentFlash]);
            mCameraPhotoView.setFlash(FLASH_OPTIONS[mCurrentFlash]);
        }
    }

    public static CapturePhotoFragment newInstance() {
        return new CapturePhotoFragment();
    }

    private void initViews() {
        if (mCameraPhotoView != null) {
            mCameraPhotoView.addCallback(mCallback);
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mCameraPhotoView.getLayoutParams();
            lp.height = getResources().getDisplayMetrics().widthPixels;
            mCameraPhotoView.setLayoutParams(lp);
            mShutter.setLayoutParams(lp);
        }
    }

    private void animateShutter() {
        mShutter.setVisibility(View.VISIBLE);
        mShutter.setAlpha(0.f);

        ObjectAnimator alphaInAnim = ObjectAnimator.ofFloat(mShutter, "alpha", 0f, 0.8f);
        alphaInAnim.setDuration(100);
        alphaInAnim.setStartDelay(100);
        alphaInAnim.setInterpolator(ACCELERATE_INTERPOLATOR);

        ObjectAnimator alphaOutAnim = ObjectAnimator.ofFloat(mShutter, "alpha", 0.8f, 0f);
        alphaOutAnim.setDuration(200);
        alphaOutAnim.setInterpolator(DECELERATE_INTERPOLATOR);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(alphaInAnim, alphaOutAnim);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mShutter.setVisibility(View.GONE);
            }
        });
        animatorSet.start();
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            HandlerThread thread = new HandlerThread("background");
            thread.start();
            mBackgroundHandler = new Handler(thread.getLooper());
        }
        return mBackgroundHandler;
    }

    private CameraView.Callback mCallback
            = new CameraView.Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
            getBackgroundHandler().post(() -> {
                File dirDest = FileUtils.getLocalDir();
                File file;
                if (dirDest.exists()) {
                    file = new File(FileUtils.getNewFilePath());
                } else {
                    if (dirDest.mkdir()) {
                        file = new File(FileUtils.getNewFilePath());
                    } else {
                        file = null;
                    }
                }
                OutputStream os = null;
                if (file != null) {
                    try {
                        os = new FileOutputStream(file);
                        BitmapFactory.Options option = new BitmapFactory.Options();
                        option.inJustDecodeBounds = true;
                        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, option);
                        option.inSampleSize = calculateInSampleSize(option, 1000, 1000);
                        option.inJustDecodeBounds = false;
                        bmp = BitmapFactory.decodeByteArray(data, 0, data.length, option);
                        bmp.compress(Bitmap.CompressFormat.JPEG, 75, os);
                    } catch (IOException e) {
                        // Cannot write
                    } finally {
                        if (os != null) {
                            try {
                                os.flush();
                                os.close();
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                    mSession.setFileToUpload(file);
                    listener.openEditor();
                }
            });
        }
    };

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraPhotoView.start();
    }

    @Override
    public void onPause() {
        mCameraPhotoView.stop();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBackgroundHandler != null) {
            mBackgroundHandler.getLooper().quitSafely();
            mBackgroundHandler = null;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (CapturePhotoFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement CapturePhotoFragmentListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.capture_photo_view, container, false);
        ButterKnife.bind(this, v);
        initViews();
        return v;
    }

}