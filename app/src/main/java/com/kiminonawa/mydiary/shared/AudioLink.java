package com.kiminonawa.mydiary.shared;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links of audio files that can be put inside a diary.
 * <p>
 * A link of Google Drive ("share" link) is changed into the address that downloads the file, so the
 * phone can play it. The file has to be shared as "Anyone with the link" and should be small
 * (Google asks for a confirmation for big files and then it can't be played inside the app).
 */
public class AudioLink {

    private static final Pattern FILE_PATH = Pattern.compile("/file/d/([A-Za-z0-9_-]+)");
    private static final Pattern ID_PARAM = Pattern.compile("[?&]id=([A-Za-z0-9_-]+)");

    /**
     * Is it something we can try to play? (an address that starts with http:// or https://)
     */
    public static boolean isValid(String link) {
        if (link == null) {
            return false;
        }
        String text = link.trim().toLowerCase();
        return (text.startsWith("http://") || text.startsWith("https://")) && text.length() > 10
                && text.indexOf(' ') < 0;
    }

    public static boolean isGoogleDrive(String link) {
        return driveFileId(link) != null;
    }

    /**
     * @return the id of the file in Google Drive, or null when the link is not from Google Drive
     */
    public static String driveFileId(String link) {
        if (link == null) {
            return null;
        }
        String text = link.trim();
        String lower = text.toLowerCase();
        if (!(lower.contains("drive.google.com") || lower.contains("docs.google.com"))) {
            return null;
        }
        Matcher matcher = FILE_PATH.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = ID_PARAM.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * The address that the player has to open.
     */
    public static String toPlayableUrl(String link) {
        String id = driveFileId(link);
        if (id != null) {
            return "https://drive.google.com/uc?export=download&id=" + id;
        }
        return link == null ? "" : link.trim();
    }

    /**
     * Short text to show as the name of the audio.
     */
    public static String displayName(String link) {
        if (isGoogleDrive(link)) {
            return "Google Drive";
        }
        try {
            String host = android.net.Uri.parse(link.trim()).getHost();
            return host == null ? link : host;
        } catch (Exception e) {
            return link;
        }
    }
}
