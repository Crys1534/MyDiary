package com.kiminonawa.mydiary.shared;

import android.app.Application;
import android.content.res.Resources;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.imagepipeline.listener.RequestListener;
import com.facebook.imagepipeline.listener.RequestLoggingListener;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Created by daxia on 2017/1/10.
 */

public class MyDiaryApplication extends Application {

    boolean hasPassword = false;

    @Override
    public void onCreate() {
        super.onCreate();
        //Use Fresco
        Set<RequestListener> listeners = new HashSet<>();
        listeners.add(new RequestLoggingListener());
        ImagePipelineConfig config = ImagePipelineConfig.newBuilder(this)
                .setRequestListeners(listeners)
                .setDownsampleEnabled(true)
                .build();
        Fresco.initialize(this, config);

        //Light / dark look (Settings): like the phone, light or dark
        applyNightMode(SPFManager.getNightMode(this));
        //The sounds of the diary
        Sfx.init(this);

        //Check password
        if (SPFManager.getPassword(this).equals("")) {
            hasPassword = false;
        } else {
            hasPassword = true;
        }

        //init Theme & language
        initTheme();
        setLocaleLanguage();

    }

    private void initTheme() {
        ThemeManager themeManager = ThemeManager.getInstance();
        themeManager.setCurrentTheme(SPFManager.getTheme(this));
    }

    /**
     * The language chosen in Settings (0 = the one of the phone, 1 = English, 2 = Spanish).
     * Android itself changes the language of all the screens (the same way the "language of each app"
     * of the phone settings does), so nothing has to be killed or restarted by hand.
     */
    private void setLocaleLanguage() {
        int code = SPFManager.getLocalLanguageCode(this);
        if (code == 0) {
            //The language of the phone: nothing to force
            return;
        }
        applyLanguage(code);
    }

    /**
     * @param mode 0 = like the phone, 1 = light, 2 = dark
     */
    public static void applyNightMode(int mode) {
        switch (mode) {
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * @param code 0 = language of the phone, 1 = English, 2 = Spanish
     */
    public static void applyLanguage(int code) {
        LocaleListCompat locales;
        switch (code) {
            case 1:
                locales = LocaleListCompat.forLanguageTags("en");
                break;
            case 2:
                locales = LocaleListCompat.forLanguageTags("es");
                break;
            default:
                locales = LocaleListCompat.getEmptyLocaleList();
                break;
        }
        //The texts written in code (dates, names of the months) use the default language of Java
        if (!locales.isEmpty()) {
            Locale.setDefault(locales.get(0));
        } else {
            Locale phoneLocale = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration()).get(0);
            if (phoneLocale != null) {
                Locale.setDefault(phoneLocale);
            }
        }
        AppCompatDelegate.setApplicationLocales(locales);
    }


    public boolean isHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

}
