package com.kiminonawa.mydiary.shared;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
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

    // ---- more things of the sky
    private static WeakReference<EsotericView> active = new WeakReference<>(null);

    private static class Particle {
        float x, y, vx, vy, life, maxLife, size;
        int color;
        //0 spark, 1 smoke, 2 falling star
        int kind;
    }

    private final ArrayList<Particle> particles = new ArrayList<>();
    private final float[][] ripples = new float[6][3]; //x, y, age (age < 0 = free)
    private float quillX, quillY, quillAge = 99f;
    private float flash = 0f;
    private float candleFlare = 0f;
    private float nextRandomSoundAt = 20f;
    private float nextThunderAt = 14f;
    //The tarot card that turns when the notebook is opened: age in seconds (>= 5 = not shown)
    private float cardAge = 99f;
    private int cardIndex = 0;
    private String[] cardNames;
    private final Handler handler = new Handler(Looper.getMainLooper());

    //Background sound: 0 silence, 1 music, 2 rain, 3 clock
    private static final String PREF_AMBIENT = "esoteric_ambient";
    private int ambientMode = 0;
    private MediaPlayer ambientPlayer;
    private String[] ambientNames;
    private String ambientLabel = null;
    private float ambientLabelAge = 99f;

    private Shader skyShader;
    private int shaderW = -1, shaderH = -1;

    public EsotericView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        messages = context.getResources().getStringArray(R.array.esoteric_oracle_messages);
        cardNames = context.getResources().getStringArray(R.array.esoteric_tarot_names);
        ambientNames = context.getResources().getStringArray(R.array.esoteric_ambient_names);
        cardIndex = rnd.nextInt(cardNames.length);
        active = new WeakReference<>(this);
        try {
            ambientMode = context.getSharedPreferences("CONFIG", Context.MODE_PRIVATE).getInt(PREF_AMBIENT, 0);
        } catch (Exception e) {
            ambientMode = 0;
        }
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

    //The animation is driven by the screen itself (not by ValueAnimator, which stops when the phone has the
    //animations turned off in the developer options or the battery saver)
    private long lastNanos = 0;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastNanos = 0;
        startAmbient();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            startAmbient();
        } else {
            stopAmbient();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAmbient();
        handler.removeCallbacksAndMessages(null);
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
        quillAge += dt;
        cardAge += dt;
        ambientLabelAge += dt;
        if (flash > 0) {
            flash = Math.max(0f, flash - dt * 1.6f);
        }
        if (candleFlare > 0) {
            candleFlare = Math.max(0f, candleFlare - dt * 0.8f);
        }
        for (int i = 0; i < ripples.length; i++) {
            if (ripples[i][2] >= 0) {
                ripples[i][2] += dt;
                if (ripples[i][2] > 1.2f) {
                    ripples[i][2] = -1f;
                }
            }
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle q = particles.get(i);
            q.life += dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            if (q.kind == 0) {
                q.vy += 60 * dp * dt;
            } else if (q.kind == 1) {
                q.vx += (float) Math.sin(time * 3 + i) * 10 * dp * dt;
                q.size += 6 * dp * dt;
            }
            if (q.life >= q.maxLife || q.y > getHeight() + 30 * dp) {
                particles.remove(i);
            }
        }
        //From time to time: a raven far away or wind chimes
        if (time > nextRandomSoundAt) {
            nextRandomSoundAt = time + 30f + rnd.nextFloat() * 40f;
            Sfx.play(getContext(), rnd.nextBoolean() ? Sfx.Sound.ESO_RAVEN : Sfx.Sound.ESO_CHIMES);
        }
        //Rain mode: thunder and a flash of light
        if (ambientMode == 2 && time > nextThunderAt) {
            nextThunderAt = time + 14f + rnd.nextFloat() * 16f;
            flash = 1f;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Sfx.play(getContext(), Sfx.Sound.ESO_THUNDER);
                }
            }, 700);
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

    private RectF ambientBounds() {
        return new RectF(6 * dp, 6 * dp, 50 * dp, 50 * dp);
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
                || candleBounds().contains(x, y))) || moonBounds().contains(x, y)
                || ambientBounds().contains(x, y);
        if (action == MotionEvent.ACTION_DOWN) {
            return onObject;
        }
        if (action == MotionEvent.ACTION_UP) {
            Context c = getContext();
            if (ambientBounds().contains(x, y)) {
                setAmbientMode((ambientMode + 1) % 4);
                return true;
            }
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
        long nowNanos = System.nanoTime();
        if (lastNanos != 0) {
            step(Math.min(0.1f, (nowNanos - lastNanos) / 1e9f));
        }
        lastNanos = nowNanos;
        postInvalidateOnAnimation();
        if (skyShader == null || shaderW != w || shaderH != h) {
            skyShader = new LinearGradient(0, 0, 0, h, 0x7A10063A, 0x7A3A1058, Shader.TileMode.CLAMP);
            shaderW = w;
            shaderH = h;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(skyShader);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        drawStars(canvas, w, h);
        drawMoonRays(canvas, w, h);
        drawShootingStar(canvas, w, h);
        drawDust(canvas, w, h);
        drawMoon(canvas);
        drawFog(canvas, w, h);
        if (objectsVisible) {
            drawIncense(canvas);
            drawCandle(canvas);
            drawBall(canvas);
            drawCat(canvas);
        }
        drawParticles(canvas);
        drawRipples(canvas);
        drawSparkle(canvas);
        drawMessage(canvas, w, h);
        drawTarotCard(canvas, w, h);
        drawAmbientButton(canvas);
        drawQuill(canvas);
        if (flash > 0) {
            paint.setColor(((int) (150 * flash) << 24) | 0xE8E0FF);
            canvas.drawRect(0, 0, w, h, paint);
        }
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

    /**
     * Age of the moon as 0..1 (0 new moon, 0.5 full moon), from the real date.
     */
    static float moonPhase() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        double days = (now.getTimeInMillis() / 86400000.0) - 10962.76; // since the new moon of 2000-01-06
        double synodic = 29.530588853;
        double p = (days % synodic) / synodic;
        return (float) (p < 0 ? p + 1 : p);
    }

    private void drawMoon(Canvas canvas) {
        float cx = getWidth() - 46 * dp, cy = 52 * dp, r = 17 * dp;
        float phase = moonPhase();
        paint.setShader(new RadialGradient(cx, cy, r * 2.6f, 0x55FFF2C0, 0x00FFF2C0, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 2.6f, paint);
        paint.setShader(null);
        //the dark part of the moon
        paint.setColor(0x882A1A4A);
        canvas.drawCircle(cx, cy, r, paint);
        //the lit part: half a circle plus (or minus) half an ellipse. Waxing: lit on the right. Waning: on the left.
        boolean waning = phase > 0.5f;
        float k = (float) Math.cos(2 * Math.PI * phase);
        canvas.save();
        if (waning) {
            canvas.scale(-1f, 1f, cx, cy);
        }
        path.reset();
        rect.set(cx - r, cy - r, cx + r, cy + r);
        path.arcTo(rect, -90, 180);
        float er = Math.max(0.5f, Math.abs(k) * r);
        rect.set(cx - er, cy - r, cx + er, cy + r);
        path.arcTo(rect, 90, k > 0 ? -180 : 180);
        path.close();
        paint.setColor(0xFFF6E7B0);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private void drawMoonRays(Canvas canvas, int w, int h) {
        float mx = w - 46 * dp, my = 52 * dp;
        float pulse = 0.6f + 0.4f * (float) Math.sin(time * 0.5f);
        int a = (int) (26 * pulse);
        for (int i = 0; i < 3; i++) {
            float spread = (0.18f + i * 0.22f);
            float ex = mx - h * (0.35f + i * 0.35f);
            float ey = h * (0.75f + i * 0.1f);
            paint.setShader(new LinearGradient(mx, my, ex, ey, (a << 24) | 0xE8E4FF, 0x00E8E4FF, Shader.TileMode.CLAMP));
            path.reset();
            path.moveTo(mx - 6 * dp, my);
            path.lineTo(mx + 6 * dp, my);
            path.lineTo(ex + h * spread * 0.55f, ey);
            path.lineTo(ex - h * spread * 0.55f, ey);
            path.close();
            canvas.drawPath(path, paint);
        }
        paint.setShader(null);
    }

    private void drawFog(Canvas canvas, int w, int h) {
        for (int i = 0; i < 3; i++) {
            float span = w * 1.8f;
            float x = ((time * (6 + i * 4) * dp + i * span / 3f) % span) - w * 0.4f;
            float y = h - 40 * dp - i * 18 * dp;
            float r = (110 + i * 30) * dp;
            paint.setShader(new RadialGradient(x, y, r, 0x30C9B8FF, 0x00C9B8FF, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, paint);
        }
        paint.setShader(null);
    }

    private void drawIncense(Canvas canvas) {
        float b = shelfBottom();
        float x0 = 142 * dp;
        //holder (a small wooden boat) and the stick
        paint.setColor(0xFF5A3A1E);
        rect.set(x0 - 16 * dp, b - 6 * dp, x0 + 16 * dp, b);
        canvas.drawRoundRect(rect, 3 * dp, 3 * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * dp);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF8A5A2E);
        canvas.drawLine(x0 - 8 * dp, b - 4 * dp, x0 + 6 * dp, b - 34 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
        //ember
        float glow = 0.6f + 0.4f * (float) Math.sin(time * 4);
        paint.setColor(((int) (255 * glow) << 24) | 0xFF5A2A);
        canvas.drawCircle(x0 + 6 * dp, b - 34 * dp, 1.6f * dp, paint);
        //smoke going up slowly in a wavy line
        for (int i = 0; i < 38; i++) {
            float yy = b - 36 * dp - i * 3.4f * dp;
            float xx = x0 + 6 * dp + (float) Math.sin(time * 0.9f + i * 0.28f) * (1.5f + i * 0.35f) * dp;
            int alpha = (int) (90 * (1 - i / 38f));
            paint.setColor((alpha << 24) | 0xD9D2E6);
            canvas.drawCircle(xx, yy, (1.2f + i * 0.09f) * dp, paint);
        }
    }

    private void drawParticles(Canvas canvas) {
        for (Particle q : particles) {
            float f = 1f - q.life / q.maxLife;
            if (q.kind == 0) {
                paint.setColor(((int) (255 * f) << 24) | (q.color & 0xFFFFFF));
                canvas.drawCircle(q.x, q.y, q.size * dp * (0.4f + f * 0.6f), paint);
            } else if (q.kind == 1) {
                paint.setColor(((int) (120 * f) << 24) | 0xC9C2D6);
                canvas.drawCircle(q.x, q.y, q.size, paint);
            } else {
                paint.setColor(((int) (255 * Math.min(1f, f * 2)) << 24) | 0xFFE9A8);
                paint.setStrokeWidth(1.6f * dp);
                canvas.drawLine(q.x, q.y, q.x - q.vx * 0.06f, q.y - q.vy * 0.06f, paint);
                canvas.drawCircle(q.x, q.y, 2 * dp, paint);
            }
        }
    }

    private void drawRipples(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        for (float[] rp : ripples) {
            if (rp[2] < 0) {
                continue;
            }
            float f = rp[2] / 1.2f;
            paint.setStrokeWidth(2 * dp * (1 - f) + 0.5f);
            paint.setColor(((int) (140 * (1 - f)) << 24) | 0xC9B8FF);
            canvas.drawCircle(rp[0], rp[1], (6 + f * 46) * dp, paint);
            paint.setColor(((int) (90 * (1 - f)) << 24) | 0xC9B8FF);
            canvas.drawCircle(rp[0], rp[1], (3 + f * 28) * dp, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawQuill(Canvas canvas) {
        if (quillAge > 1.4f) {
            return;
        }
        float a = Math.max(0f, 1f - quillAge / 1.4f);
        canvas.save();
        canvas.translate(quillX, quillY);
        canvas.rotate(-35);
        //feather
        paint.setColor(((int) (210 * a) << 24) | 0xF1E4FF);
        rect.set(-4 * dp, -30 * dp, 4 * dp, -3 * dp);
        canvas.drawOval(rect, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * dp);
        paint.setColor(((int) (230 * a) << 24) | 0xB89A4A);
        canvas.drawLine(0, 0, 0, -28 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
        //ink dot
        paint.setColor(((int) (200 * a) << 24) | 0x2A1A4A);
        canvas.drawCircle(quillX, quillY, 1.8f * dp, paint);
    }

    private void drawAmbientButton(Canvas canvas) {
        RectF r = ambientBounds();
        float cx = r.centerX(), cy = r.centerY();
        paint.setColor(0x66100820);
        canvas.drawCircle(cx, cy, 15 * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * dp);
        paint.setColor(ambientMode == 0 ? 0x88D4AF37 : 0xFFD4AF37);
        canvas.drawCircle(cx, cy, 15 * dp, paint);
        //a note
        paint.setStyle(Paint.Style.FILL);
        rect.set(cx - 6 * dp, cy + 1 * dp, cx + 1 * dp, cy + 6 * dp);
        canvas.drawOval(rect, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * dp);
        canvas.drawLine(cx + 0.5f * dp, cy + 3 * dp, cx + 0.5f * dp, cy - 7 * dp, paint);
        canvas.drawLine(cx + 0.5f * dp, cy - 7 * dp, cx + 6 * dp, cy - 4 * dp, paint);
        if (ambientMode == 0) {
            canvas.drawLine(cx - 9 * dp, cy + 9 * dp, cx + 9 * dp, cy - 9 * dp, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (ambientLabel != null && ambientLabelAge < 2.4f) {
            float a = ambientLabelAge > 1.8f ? (2.4f - ambientLabelAge) / 0.6f : 1f;
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(13 * dp);
            paint.setColor(((int) (230 * a) << 24) | 0xF1E4FF);
            canvas.drawText(ambientLabel, r.right + 6 * dp, cy + 5 * dp, paint);
        }
    }

    /**
     * A tarot card turns over when the notebook is opened: first its back, then the front with its name.
     */
    private void drawTarotCard(Canvas canvas, int w, int h) {
        if (cardAge >= 5f) {
            return;
        }
        float alpha = cardAge < 0.4f ? cardAge / 0.4f : (cardAge > 4.2f ? Math.max(0f, (5f - cardAge) / 0.8f) : 1f);
        float turn = Math.min(1f, Math.max(0f, (cardAge - 1.0f) / 1.0f));
        float scaleX = (float) Math.abs(Math.cos(Math.PI * turn));
        boolean front = turn >= 0.5f;
        float cw = 104 * dp, ch = 170 * dp;
        float cx = w / 2f, cy = h * 0.42f;
        canvas.save();
        canvas.scale(Math.max(0.02f, scaleX), 1f, cx, cy);
        rect.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2);
        paint.setColor(((int) (245 * alpha) << 24) | (front ? 0x14102E : 0x3B1E6B));
        canvas.drawRoundRect(rect, 10 * dp, 10 * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(((int) (255 * alpha) << 24) | 0xD4AF37);
        canvas.drawRoundRect(rect, 10 * dp, 10 * dp, paint);
        rect.inset(7 * dp, 7 * dp);
        paint.setStrokeWidth(1 * dp);
        canvas.drawRoundRect(rect, 6 * dp, 6 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(((int) (255 * alpha) << 24) | 0xD4AF37);
        if (!front) {
            drawStarShape(canvas, cx, cy, 30 * dp, 5, 0.45f);
            canvas.drawCircle(cx, cy, 6 * dp, paint);
        } else {
            int kind = cardIndex % 3;
            if (kind == 0) {
                //the moon
                paint.setColor(((int) (255 * alpha) << 24) | 0xF6E7B0);
                canvas.drawCircle(cx, cy - 12 * dp, 24 * dp, paint);
                paint.setColor(((int) (255 * alpha) << 24) | 0x14102E);
                canvas.drawCircle(cx + 11 * dp, cy - 17 * dp, 21 * dp, paint);
            } else if (kind == 1) {
                drawStarShape(canvas, cx, cy - 12 * dp, 30 * dp, 8, 0.4f);
            } else {
                //the sun
                canvas.drawCircle(cx, cy - 12 * dp, 14 * dp, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * dp);
                for (int i = 0; i < 12; i++) {
                    double ang = i * Math.PI / 6;
                    canvas.drawLine(cx + (float) Math.cos(ang) * 19 * dp, cy - 12 * dp + (float) Math.sin(ang) * 19 * dp,
                            cx + (float) Math.cos(ang) * 28 * dp, cy - 12 * dp + (float) Math.sin(ang) * 28 * dp, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(((int) (255 * alpha) << 24) | 0xF1E4FF);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(13 * dp);
            canvas.drawText(cardNames[cardIndex], cx, cy + ch / 2 - 20 * dp, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
        canvas.restore();
    }

    private void drawStarShape(Canvas canvas, float cx, float cy, float r, int points, float inner) {
        path.reset();
        for (int i = 0; i < points * 2; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / points;
            float rr = (i % 2 == 0) ? r : r * inner;
            float px = cx + (float) Math.cos(ang) * rr, py = cy + (float) Math.sin(ang) * rr;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    // ------------------------------------------------------------------ things that happen

    /**
     * The diary played a sound: the sky answers (sparks when saving, smoke when deleting...).
     */
    public static void onSound(Sfx.Sound sound) {
        EsotericView v = active.get();
        if (v == null) {
            return;
        }
        switch (sound) {
            case SAVE:
                v.sparks(v.getWidth() / 2f, v.getHeight() * 0.55f, 42);
                break;
            case CRUMPLE:
                v.smokePuff(v.getWidth() / 2f, v.getHeight() * 0.6f);
                break;
            case NOTEBOOK_OPEN:
                v.candleOn = true;
                v.candleFlare = 1f;
                v.cardAge = 0f;
                v.cardIndex = v.rnd.nextInt(v.cardNames.length);
                v.handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Sfx.play(getContextSafe(), Sfx.Sound.ESO_WHISPER);
                    }
                }, 900);
                break;
            case NOTEBOOK_CLOSE:
                v.candleOn = false;
                break;
            default:
                break;
        }
    }

    private static Context getContextSafe() {
        EsotericView v = active.get();
        return v == null ? null : v.getContext();
    }

    private void sparks(float x, float y, int count) {
        for (int i = 0; i < count && particles.size() < 160; i++) {
            Particle q = new Particle();
            double ang = rnd.nextDouble() * Math.PI * 2;
            float sp = (40 + rnd.nextFloat() * 170) * dp;
            q.x = x;
            q.y = y;
            q.vx = (float) Math.cos(ang) * sp;
            q.vy = (float) Math.sin(ang) * sp - 60 * dp;
            q.maxLife = 0.7f + rnd.nextFloat() * 0.8f;
            q.size = 1.2f + rnd.nextFloat() * 1.8f;
            q.color = rnd.nextInt(3) == 0 ? 0xFFFFFF : (rnd.nextBoolean() ? 0xFFD27A : 0xFFE9A8);
            q.kind = 0;
            particles.add(q);
        }
    }

    private void smokePuff(float x, float y) {
        for (int i = 0; i < 14 && particles.size() < 160; i++) {
            Particle q = new Particle();
            q.x = x + (rnd.nextFloat() - 0.5f) * 40 * dp;
            q.y = y + (rnd.nextFloat() - 0.5f) * 14 * dp;
            q.vx = (rnd.nextFloat() - 0.5f) * 16 * dp;
            q.vy = -(18 + rnd.nextFloat() * 30) * dp;
            q.maxLife = 1.6f + rnd.nextFloat();
            q.size = (5 + rnd.nextFloat() * 6) * dp;
            q.kind = 1;
            particles.add(q);
        }
    }

    /**
     * The phone was shaken: stars fall from the sky.
     */
    public void shake() {
        for (int i = 0; i < 34 && particles.size() < 190; i++) {
            Particle q = new Particle();
            q.x = rnd.nextFloat() * getWidth();
            q.y = -rnd.nextFloat() * getHeight() * 0.4f;
            q.vx = -(40 + rnd.nextFloat() * 60) * dp;
            q.vy = (150 + rnd.nextFloat() * 220) * dp;
            q.maxLife = 2.5f;
            q.size = 2f;
            q.kind = 2;
            particles.add(q);
        }
        flash = 0.35f;
        Sfx.play(getContext(), Sfx.Sound.ESO_SHIMMER);
        Sfx.play(getContext(), Sfx.Sound.ESO_CHIMES);
    }

    /**
     * Touch anywhere on the screen (called by the activity): a ripple like in a magic pond and the quill.
     */
    public void touched(float screenX, float screenY, boolean down) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float x = screenX - loc[0], y = screenY - loc[1];
        quillX = x;
        quillY = y;
        quillAge = 0f;
        if (down) {
            for (float[] rp : ripples) {
                if (rp[2] < 0 || rp[2] > 1.2f) {
                    rp[0] = x;
                    rp[1] = y;
                    rp[2] = 0f;
                    break;
                }
            }
        }
    }

    private void setAmbientMode(int mode) {
        ambientMode = mode;
        try {
            SharedPreferences.Editor e = getContext().getSharedPreferences("CONFIG", Context.MODE_PRIVATE).edit();
            e.putInt(PREF_AMBIENT, mode);
            e.apply();
        } catch (Exception ignored) {
        }
        ambientLabel = ambientNames[mode];
        ambientLabelAge = 0f;
        stopAmbient();
        startAmbient();
        Sfx.play(getContext(), Sfx.Sound.ESO_BELL);
    }

    private void startAmbient() {
        if (ambientMode == 0 || ambientPlayer != null || !isAttachedToWindow()
                || !SPFManager.isSoundOn(getContext())) {
            return;
        }
        try {
            int res = ambientMode == 1 ? R.raw.eso_amb_music : (ambientMode == 2 ? R.raw.eso_amb_rain : R.raw.eso_amb_clock);
            ambientPlayer = MediaPlayer.create(getContext(), res);
            if (ambientPlayer == null) {
                return;
            }
            float vol = SPFManager.getSoundVolume(getContext()) / 100f * (ambientMode == 3 ? 0.9f : 0.6f);
            ambientPlayer.setVolume(vol, vol);
            ambientPlayer.setLooping(true);
            ambientPlayer.start();
        } catch (Exception e) {
            ambientPlayer = null;
        }
    }

    private void stopAmbient() {
        if (ambientPlayer != null) {
            try {
                ambientPlayer.stop();
                ambientPlayer.release();
            } catch (Exception ignored) {
            }
            ambientPlayer = null;
        }
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
            float fh = (17 + flick * 2.5f + candleFlare * 8f) * dp;
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
