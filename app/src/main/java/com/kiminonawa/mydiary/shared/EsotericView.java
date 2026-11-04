package com.kiminonawa.mydiary.shared;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.kiminonawa.mydiary.R;

import java.util.Random;

/**
 * The "Esoteric" look of a category: a night sky over the pages (twinkling stars, a moon, magic dust, shooting stars)
 * and, on the shelf above the bottom bar, a sleeping black cat, a candle that flickers and a crystal ball that gives
 * messages. Everything is drawn with code (no pictures) and the empty parts do not catch touches, so the diary under
 * it works as always. Tap the cat, the candle, the ball or the moon.
 */
public class EsotericView extends View {

    private static final int STARS = 46;
    private static final int DUST = 20;

    private final float dp;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private final float[] starX = new float[STARS], starY = new float[STARS], starSize = new float[STARS],
            starPhase = new float[STARS], starSpeed = new float[STARS];
    private final float[] dustX = new float[DUST], dustY = new float[DUST], dustSize = new float[DUST],
            dustSpeed = new float[DUST], dustPhase = new float[DUST];

    private ValueAnimator clock;
    //Seconds since the view is shown
    private float time = 0f;

    private boolean objectsVisible = true;
    private boolean candleOn = true;
    //0..1: how much smoke is left after the candle was blown out
    private float smoke = 0f;
    private float catHappy = 0f;
    private String message = null;
    private float messageAge = 99f;
    private int messageIndex = 0;
    private String[] messages;
    //shooting star: progress 0..1, <0 = none
    private float shoot = -1f;
    private float shootY = 0f;
    private float nextShootAt = 6f;
    private float sparkle = 0f;
    private float sparkleX, sparkleY;

    private Shader skyShader;
    private int shaderW = -1, shaderH = -1;

    public EsotericView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        messages = context.getResources().getStringArray(R.array.esoteric_oracle_messages);
        for (int i = 0; i < STARS; i++) {
            starX[i] = rnd.nextFloat();
            starY[i] = rnd.nextFloat();
            starSize[i] = 0.5f + rnd.nextFloat() * 1.6f;
            starPhase[i] = rnd.nextFloat() * 6.28f;
            starSpeed[i] = 0.8f + rnd.nextFloat() * 2f;
        }
        for (int i = 0; i < DUST; i++) {
            dustX[i] = rnd.nextFloat();
            dustY[i] = rnd.nextFloat();
            dustSize[i] = 0.8f + rnd.nextFloat() * 1.4f;
            dustSpeed[i] = 0.01f + rnd.nextFloat() * 0.025f;
            dustPhase[i] = rnd.nextFloat() * 6.28f;
        }
        messageIndex = rnd.nextInt(messages.length);
    }

    public void setObjectsVisible(boolean visible) {
        objectsVisible = visible;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        clock = ValueAnimator.ofFloat(0f, 1f);
        clock.setDuration(1000);
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.setInterpolator(new LinearInterpolator());
        final long[] last = {System.nanoTime()};
        clock.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                long now = System.nanoTime();
                float dt = Math.min(0.1f, (now - last[0]) / 1e9f);
                last[0] = now;
                step(dt);
                invalidate();
            }
        });
        clock.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (clock != null) {
            clock.cancel();
            clock = null;
        }
        super.onDetachedFromWindow();
    }

    private void step(float dt) {
        time += dt;
        messageAge += dt;
        if (smoke > 0) {
            smoke = Math.max(0f, smoke - dt * 0.45f);
        }
        if (catHappy > 0) {
            catHappy = Math.max(0f, catHappy - dt * 0.5f);
        }
        if (sparkle > 0) {
            sparkle = Math.max(0f, sparkle - dt * 1.2f);
        }
        for (int i = 0; i < DUST; i++) {
            dustY[i] -= dustSpeed[i] * dt;
            if (dustY[i] < -0.05f) {
                dustY[i] = 1.05f;
                dustX[i] = rnd.nextFloat();
            }
        }
        if (shoot >= 0) {
            shoot += dt / 1.1f;
            if (shoot > 1f) {
                shoot = -1f;
            }
        } else if (time > nextShootAt) {
            startShootingStar();
        }
    }

    private void startShootingStar() {
        shoot = 0f;
        shootY = 0.05f + rnd.nextFloat() * 0.3f;
        nextShootAt = time + 9f + rnd.nextFloat() * 14f;
    }

    // ------------------------------------------------------------------ touch

    private float shelfBottom() {
        return getHeight() - 8 * dp;
    }

    private RectF catBounds() {
        float b = shelfBottom();
        return new RectF(6 * dp, b - 62 * dp, 112 * dp, b);
    }

    private RectF ballBounds() {
        float b = shelfBottom();
        float cx = getWidth() - 100 * dp;
        return new RectF(cx - 28 * dp, b - 58 * dp, cx + 28 * dp, b);
    }

    private RectF candleBounds() {
        float b = shelfBottom();
        float cx = getWidth() - 36 * dp;
        return new RectF(cx - 22 * dp, b - 92 * dp, cx + 22 * dp, b);
    }

    private RectF moonBounds() {
        float cx = getWidth() - 46 * dp;
        float cy = 52 * dp;
        return new RectF(cx - 34 * dp, cy - 34 * dp, cx + 34 * dp, cy + 34 * dp);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX(), y = event.getY();
        boolean onObject = (objectsVisible && (catBounds().contains(x, y) || ballBounds().contains(x, y)
                || candleBounds().contains(x, y))) || moonBounds().contains(x, y);
        if (action == MotionEvent.ACTION_DOWN) {
            return onObject;
        }
        if (action == MotionEvent.ACTION_UP) {
            Context c = getContext();
            if (moonBounds().contains(x, y)) {
                Sfx.play(c, Sfx.Sound.ESO_BELL);
                startShootingStar();
                return true;
            }
            if (!objectsVisible) {
                return false;
            }
            if (catBounds().contains(x, y)) {
                catHappy = 1f;
                sparkle = 1f;
                sparkleX = x;
                sparkleY = y;
                Sfx.play(c, rnd.nextInt(4) == 0 ? Sfx.Sound.ESO_MEOW : Sfx.Sound.ESO_PURR);
                Haptics.saved(c);
            } else if (ballBounds().contains(x, y)) {
                messageIndex = (messageIndex + 1) % messages.length;
                message = messages[messageIndex];
                messageAge = 0f;
                sparkle = 1f;
                sparkleX = x;
                sparkleY = y;
                Sfx.play(c, Sfx.Sound.ESO_BELL);
                Sfx.play(c, Sfx.Sound.ESO_SHIMMER);
            } else if (candleBounds().contains(x, y)) {
                candleOn = !candleOn;
                if (!candleOn) {
                    smoke = 1f;
                    Sfx.play(c, Sfx.Sound.ESO_CANDLE_OUT);
                } else {
                    Sfx.play(c, Sfx.Sound.ESO_CANDLE_ON);
                }
            }
            return true;
        }
        return onObject;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        if (skyShader == null || shaderW != w || shaderH != h) {
            skyShader = new LinearGradient(0, 0, 0, h, 0x3A12063A, 0x3A4A1466, Shader.TileMode.CLAMP);
            shaderW = w;
            shaderH = h;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(skyShader);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        drawStars(canvas, w, h);
        drawShootingStar(canvas, w, h);
        drawDust(canvas, w, h);
        drawMoon(canvas);
        if (objectsVisible) {
            drawCandle(canvas);
            drawBall(canvas);
            drawCat(canvas);
        }
        drawSparkle(canvas);
        drawMessage(canvas, w, h);
    }

    private void drawStars(Canvas canvas, int w, int h) {
        for (int i = 0; i < STARS; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(time * starSpeed[i] + starPhase[i]);
            int alpha = (int) (40 + 150 * tw);
            paint.setColor((alpha << 24) | 0xFFF4C8);
            float r = starSize[i] * dp;
            float x = starX[i] * w, y = starY[i] * h;
            if (starSize[i] > 1.6f) {
                //a four-point star
                path.reset();
                float l = r * 3.2f;
                path.moveTo(x, y - l);
                path.quadTo(x, y, x + l, y);
                path.quadTo(x, y, x, y + l);
                path.quadTo(x, y, x - l, y);
                path.quadTo(x, y, x, y - l);
                canvas.drawPath(path, paint);
            } else {
                canvas.drawCircle(x, y, r, paint);
            }
        }
    }

    private void drawShootingStar(Canvas canvas, int w, int h) {
        if (shoot < 0) {
            return;
        }
        float x = w * (1.1f - shoot * 1.3f);
        float y = h * shootY + shoot * h * 0.25f;
        float len = 70 * dp;
        int alpha = (int) (255 * Math.sin(Math.PI * shoot));
        paint.setStrokeWidth(2 * dp);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(new LinearGradient(x, y, x + len, y - len * 0.2f, (alpha << 24) | 0xFFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        canvas.drawLine(x, y, x + len, y - len * 0.2f, paint);
        paint.setShader(null);
        paint.setColor((alpha << 24) | 0xFFFFFF);
        canvas.drawCircle(x, y, 2.2f * dp, paint);
    }

    private void drawDust(Canvas canvas, int w, int h) {
        for (int i = 0; i < DUST; i++) {
            float sway = (float) Math.sin(time * 0.7f + dustPhase[i]) * 14 * dp;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 2.2f + dustPhase[i] * 3);
            paint.setColor(((int) (30 + 110 * tw) << 24) | 0xFFD27A);
            canvas.drawCircle(dustX[i] * w + sway, dustY[i] * h, dustSize[i] * dp, paint);
        }
    }

    private void drawMoon(Canvas canvas) {
        float cx = getWidth() - 46 * dp, cy = 52 * dp, r = 17 * dp;
        paint.setShader(new RadialGradient(cx, cy, r * 2.6f, 0x55FFF2C0, 0x00FFF2C0, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 2.6f, paint);
        paint.setShader(null);
        paint.setColor(0xFFF6E7B0);
        canvas.drawCircle(cx, cy, r, paint);
        //crescent: a bite taken with a dark circle
        paint.setColor(0xFF2A0F4A);
        canvas.drawCircle(cx + r * 0.55f, cy - r * 0.2f, r * 0.9f, paint);
    }

    private void drawCandle(Canvas canvas) {
        float b = shelfBottom();
        float cx = getWidth() - 36 * dp;
        //holder
        paint.setColor(0xFF3B2A12);
        rect.set(cx - 16 * dp, b - 8 * dp, cx + 16 * dp, b);
        canvas.drawRoundRect(rect, 3 * dp, 3 * dp, paint);
        paint.setColor(0xFFC9A24A);
        rect.set(cx - 12 * dp, b - 12 * dp, cx + 12 * dp, b - 6 * dp);
        canvas.drawRoundRect(rect, 3 * dp, 3 * dp, paint);
        //wax
        paint.setColor(0xFFEDE0C4);
        rect.set(cx - 7 * dp, b - 50 * dp, cx + 7 * dp, b - 10 * dp);
        canvas.drawRoundRect(rect, 3 * dp, 3 * dp, paint);
        //a drop of wax
        paint.setColor(0xFFD9C9A6);
        rect.set(cx + 2 * dp, b - 50 * dp, cx + 7 * dp, b - 38 * dp);
        canvas.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, paint);
        float wickY = b - 50 * dp;
        paint.setColor(0xFF222222);
        canvas.drawRect(cx - 0.7f * dp, wickY - 4 * dp, cx + 0.7f * dp, wickY, paint);
        if (candleOn) {
            float flick = (float) (Math.sin(time * 13) * 0.5 + Math.sin(time * 7.3 + 1) * 0.5);
            float fh = (17 + flick * 2.5f) * dp;
            float sway = (float) Math.sin(time * 5.1) * 1.8f * dp;
            //glow
            float gr = (40 + flick * 4) * dp;
            paint.setShader(new RadialGradient(cx, wickY - 12 * dp, gr, 0x66FFB347, 0x00FFB347, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, wickY - 12 * dp, gr, paint);
            paint.setShader(null);
            //outer flame
            path.reset();
            path.moveTo(cx, wickY - 3 * dp);
            path.cubicTo(cx - 7 * dp, wickY - 8 * dp, cx - 4 * dp + sway, wickY - fh * 0.7f, cx + sway, wickY - fh - 3 * dp);
            path.cubicTo(cx + 4 * dp + sway, wickY - fh * 0.7f, cx + 7 * dp, wickY - 8 * dp, cx, wickY - 3 * dp);
            paint.setColor(0xFFFF9A2E);
            canvas.drawPath(path, paint);
            //inner flame
            path.reset();
            path.moveTo(cx, wickY - 3 * dp);
            path.cubicTo(cx - 3.5f * dp, wickY - 6 * dp, cx - 2 * dp + sway * 0.6f, wickY - fh * 0.5f, cx + sway * 0.6f, wickY - fh * 0.7f);
            path.cubicTo(cx + 2 * dp + sway * 0.6f, wickY - fh * 0.5f, cx + 3.5f * dp, wickY - 6 * dp, cx, wickY - 3 * dp);
            paint.setColor(0xFFFFE9A0);
            canvas.drawPath(path, paint);
        }
        if (smoke > 0) {
            for (int i = 0; i < 4; i++) {
                float p = 1f - smoke;
                float yy = wickY - 4 * dp - (p * 40 + i * 6) * dp;
                float xx = cx + (float) Math.sin(time * 3 + i * 1.7f) * (3 + p * 8) * dp;
                paint.setColor(((int) (110 * smoke * (1 - i * 0.2f)) << 24) | 0xCCCCCC);
                canvas.drawCircle(xx, yy, (2 + p * 4 + i) * dp, paint);
            }
        }
    }

    private void drawBall(Canvas canvas) {
        float b = shelfBottom();
        float cx = getWidth() - 100 * dp;
        //stand
        paint.setColor(0xFF4B2E6B);
        path.reset();
        path.moveTo(cx - 20 * dp, b);
        path.lineTo(cx - 12 * dp, b - 12 * dp);
        path.lineTo(cx + 12 * dp, b - 12 * dp);
        path.lineTo(cx + 20 * dp, b);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xFFC9A24A);
        rect.set(cx - 22 * dp, b - 3 * dp, cx + 22 * dp, b);
        canvas.drawRect(rect, paint);
        //glass
        float r = 20 * dp;
        float cy = b - 12 * dp - r * 0.8f;
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 1.6f);
        paint.setShader(new RadialGradient(cx - 6 * dp, cy - 7 * dp, r * 1.6f,
                new int[]{0xFFEAD8FF, 0xFF9B6BFF, 0xFF3B1E7A}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        //mist inside
        for (int i = 0; i < 3; i++) {
            float a = time * (0.6f + i * 0.25f) + i * 2.1f;
            paint.setColor(((int) (70 * pulse) << 24) | 0xFFFFFF);
            canvas.drawCircle(cx + (float) Math.cos(a) * 8 * dp, cy + (float) Math.sin(a * 1.3f) * 7 * dp, (4 + i) * dp, paint);
        }
        //highlight
        paint.setColor(0x99FFFFFF);
        rect.set(cx - 12 * dp, cy - 14 * dp, cx - 5 * dp, cy - 9 * dp);
        canvas.drawOval(rect, paint);
        //aura
        paint.setShader(new RadialGradient(cx, cy, r * 2f, ((int) (60 * pulse) << 24) | 0xB388FF, 0x00B388FF, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 2f, paint);
        paint.setShader(null);
    }

    private void drawCat(Canvas canvas) {
        float b = shelfBottom();
        float left = 10 * dp;
        float breathe = (float) Math.sin(time * 1.5f) * 1.2f * dp * (1 + catHappy);
        int fur = 0xFF0C0A12;
        int rim = 0xFF2B2440;
        //body (a curled loaf)
        paint.setColor(fur);
        rect.set(left, b - 34 * dp - breathe, left + 78 * dp, b);
        canvas.drawOval(rect, paint);
        //back rim light
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * dp);
        paint.setColor(rim);
        rect.set(left + 2 * dp, b - 33 * dp - breathe, left + 76 * dp, b - 2 * dp);
        canvas.drawArc(rect, 200, 120, false, paint);
        paint.setStyle(Paint.Style.FILL);
        //tail sways
        float tail = (float) Math.sin(time * 1.1f) * 7 * dp;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6 * dp);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(fur);
        path.reset();
        path.moveTo(left + 70 * dp, b - 6 * dp);
        path.cubicTo(left + 92 * dp, b - 4 * dp, left + 96 * dp + tail, b - 22 * dp, left + 88 * dp + tail, b - 30 * dp);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        //head, resting on the paws
        float hx = left + 18 * dp, hy = b - 22 * dp - breathe * 0.5f;
        paint.setColor(fur);
        rect.set(hx - 17 * dp, hy - 14 * dp, hx + 17 * dp, hy + 12 * dp);
        canvas.drawOval(rect, paint);
        //ears
        for (int s = -1; s <= 1; s += 2) {
            path.reset();
            path.moveTo(hx + s * 5 * dp, hy - 11 * dp);
            path.lineTo(hx + s * 15 * dp, hy - 23 * dp);
            path.lineTo(hx + s * 16 * dp, hy - 6 * dp);
            path.close();
            canvas.drawPath(path, paint);
        }
        //closed eyes (little moons); happy = wider smile
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * dp);
        paint.setColor(0xFFE6C75A);
        for (int s = -1; s <= 1; s += 2) {
            rect.set(hx + s * 7 * dp - 3.5f * dp, hy - 5 * dp, hx + s * 7 * dp + 3.5f * dp, hy + 1 * dp);
            canvas.drawArc(rect, 20, 140, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        //nose
        paint.setColor(0xFF6A4A5A);
        canvas.drawCircle(hx, hy + 3 * dp, 1.2f * dp, paint);
        //sleepy "z" floating up
        for (int i = 0; i < 3; i++) {
            float p = ((time * 0.35f) + i / 3f) % 1f;
            paint.setColor(((int) (200 * (1 - p)) << 24) | 0xE6D9FF);
            paint.setTextSize((8 + p * 6) * dp);
            canvas.drawText("z", hx + 14 * dp + p * 18 * dp + (float) Math.sin(p * 6) * 3 * dp, hy - 16 * dp - p * 30 * dp, paint);
        }
        //happy hearts
        if (catHappy > 0) {
            paint.setColor(((int) (230 * catHappy) << 24) | 0xFF7AA8);
            paint.setTextSize(14 * dp);
            canvas.drawText("♥", hx + 34 * dp, hy - 22 * dp - (1 - catHappy) * 30 * dp, paint);
        }
    }

    private void drawSparkle(Canvas canvas) {
        if (sparkle <= 0) {
            return;
        }
        for (int i = 0; i < 7; i++) {
            float a = i * 0.9f + sparkle * 2f;
            float d = (1 - sparkle) * 38 * dp + 6 * dp;
            paint.setColor(((int) (255 * sparkle) << 24) | 0xFFE9A8);
            canvas.drawCircle(sparkleX + (float) Math.cos(a) * d, sparkleY + (float) Math.sin(a) * d, 2.2f * dp * sparkle + dp * 0.6f, paint);
        }
    }

    private void drawMessage(Canvas canvas, int w, int h) {
        if (message == null || messageAge > 6f) {
            return;
        }
        float a = messageAge < 0.8f ? messageAge / 0.8f : (messageAge > 5f ? Math.max(0f, 6f - messageAge) : 1f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(16 * dp);
        float tw = paint.measureText(message);
        float maxW = w - 40 * dp;
        float scale = tw > maxW ? maxW / tw : 1f;
        float cy = h - 140 * dp;
        paint.setColor(((int) (200 * a) << 24) | 0x1A0836);
        rect.set(w / 2f - (tw * scale) / 2 - 16 * dp, cy - 22 * dp, w / 2f + (tw * scale) / 2 + 16 * dp, cy + 12 * dp);
        canvas.drawRoundRect(rect, 18 * dp, 18 * dp, paint);
        paint.setColor(((int) (255 * a) << 24) | 0xF1E4FF);
        canvas.save();
        canvas.scale(scale, scale, w / 2f, cy);
        canvas.drawText(message, w / 2f, cy, paint);
        canvas.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
