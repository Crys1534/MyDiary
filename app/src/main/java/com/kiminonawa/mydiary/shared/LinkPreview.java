package com.kiminonawa.mydiary.shared;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "preview" of a link inside an entry: the title, the short description and the picture of the web page (what
 * the page says about itself in its "og:" / "twitter:" tags). It is read from the internet in the background the
 * first time and kept in the temporary memory of the app, so the next time it shows at once (and without internet).
 * The entry only saves the link itself: if the preview can not be read, the link is still there.
 */
public final class LinkPreview {

    public interface Callback {
        /**
         * Called on the main thread. The preview is never null; its fields can be empty.
         */
        void onPreview(LinkPreview preview);
    }

    public String url = "";
    public String title = "";
    public String description = "";
    public String siteName = "";
    /**
     * Small picture of the page (a file of the temporary memory), or null
     */
    public File imageFile = null;

    private static final String CACHE_DIR = "link_previews";
    private static final int MAX_HTML_BYTES = 600 * 1024;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int IMAGE_MAX_SIDE_PX = 480;
    private static final int TIMEOUT_MS = 9000;
    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static final Pattern META_TAG = Pattern.compile("<meta\\s[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([a-zA-Z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.DOTALL);
    private static final Pattern TITLE_TAG = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LinkPreview() {
    }

    /**
     * Makes what the person pasted a web address: spaces around are taken away and "example.com/page" becomes
     * "https://example.com/page".
     *
     * @return the address, or null when it is not something that looks like a link
     */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String link = text.trim();
        if (link.length() == 0 || link.indexOf(' ') >= 0 || link.indexOf('\n') >= 0) {
            return null;
        }
        String lower = link.toLowerCase(Locale.US);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            if (link.contains("://") || !link.contains(".") || link.startsWith(".") || link.endsWith(".")) {
                return null;
            }
            link = "https://" + link;
        }
        return link.length() > 10 ? link : null;
    }

    /**
     * The name of the site ("example.com") to show under the preview.
     */
    public static String domainOf(String url) {
        try {
            String host = new URL(url).getHost();
            if (host != null && host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host == null || host.length() == 0 ? url : host;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Gets the preview of a link: at once from the temporary memory if it is there, or reading the page in the
     * background. The callback is called on the main thread (once; with empty fields if nothing could be read).
     */
    public static void load(final Context context, final String url, final Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                LinkPreview preview = readCache(app, url);
                if (preview == null) {
                    preview = fetch(app, url);
                    if (preview.title.length() > 0 || preview.description.length() > 0 || preview.imageFile != null) {
                        writeCache(app, preview);
                    }
                }
                final LinkPreview result = preview;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onPreview(result);
                    }
                });
            }
        }).start();
    }

    // ---- reading the page ----

    private static LinkPreview fetch(Context context, String url) {
        LinkPreview preview = new LinkPreview();
        preview.url = url;
        try {
            String address = url;
            //A page without "https" is asked with "https" (the phone does not allow the plain "http" connections)
            if (address.toLowerCase(Locale.US).startsWith("http://")) {
                address = "https://" + address.substring(7);
            }
            String[] result = new String[2];
            String html = download(address, result);
            if (html == null) {
                return preview;
            }
            String finalAddress = result[0];
            Map<String, String> meta = readMeta(html);
            preview.title = clean(first(meta, "og:title", "twitter:title"), 200);
            if (preview.title.length() == 0) {
                Matcher title = TITLE_TAG.matcher(html);
                if (title.find()) {
                    preview.title = clean(title.group(1), 200);
                }
            }
            preview.description = clean(first(meta, "og:description", "twitter:description", "description"), 300);
            preview.siteName = clean(first(meta, "og:site_name"), 80);
            String image = first(meta, "og:image", "og:image:url", "twitter:image", "twitter:image:src");
            if (image != null && image.trim().length() > 0) {
                preview.imageFile = downloadImage(context, new URL(new URL(finalAddress), unescape(image.trim())).toString(), url);
            }
        } catch (Throwable ignored) {
            //No preview: the link is still shown
        }
        return preview;
    }

    /**
     * @param finalAddress receives, in [0], the address after the redirects
     * @return the text of the page (the beginning of it), or null when it is not a web page
     */
    private static String download(String address, String[] finalAddress) throws IOException {
        String current = address;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag() + ",en;q=0.7");
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        return null;
                    }
                    current = new URL(new URL(current), location).toString();
                    if (current.toLowerCase(Locale.US).startsWith("http://")) {
                        current = "https://" + current.substring(7);
                    }
                    continue;
                }
                if (code != 200) {
                    return null;
                }
                String type = connection.getContentType();
                if (type != null && !type.toLowerCase(Locale.US).contains("html")) {
                    return null;
                }
                finalAddress[0] = current;
                byte[] bytes = readBytes(connection.getInputStream(), MAX_HTML_BYTES, true);
                return decode(bytes, type);
            } finally {
                connection.disconnect();
            }
        }
        return null;
    }

    private static String decode(byte[] bytes, String contentType) {
        Charset charset = null;
        if (contentType != null) {
            charset = charsetOf(contentType);
        }
        if (charset == null) {
            //The beginning of the page says it (<meta charset="...">)
            String head = new String(bytes, 0, Math.min(bytes.length, 4096), Charset.forName("ISO-8859-1"));
            charset = charsetOf(head);
        }
        return new String(bytes, charset == null ? Charset.forName("UTF-8") : charset);
    }

    private static Charset charsetOf(String text) {
        Matcher matcher = CHARSET.matcher(text);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * @param stopAtHead the title and the "meta" tags are in the head of the page: stop reading after it
     */
    private static byte[] readBytes(InputStream in, int max, boolean stopAtHead) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (out.size() >= max) {
                    break;
                }
                if (stopAtHead && out.size() > 2048) {
                    String text = new String(out.toByteArray(), Charset.forName("ISO-8859-1")).toLowerCase(Locale.US);
                    if (text.contains("</head>")) {
                        break;
                    }
                }
            }
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        return out.toByteArray();
    }

    private static Map<String, String> readMeta(String html) {
        Map<String, String> meta = new HashMap<>();
        Matcher tag = META_TAG.matcher(html);
        while (tag.find()) {
            String key = null;
            String content = null;
            Matcher attribute = ATTRIBUTE.matcher(tag.group());
            while (attribute.find()) {
                String name = attribute.group(1).toLowerCase(Locale.US);
                String value = attribute.group(2) != null ? attribute.group(2) : attribute.group(3);
                if (name.equals("property") || name.equals("name") || name.equals("itemprop")) {
                    if (key == null) {
                        key = value.trim().toLowerCase(Locale.US);
                    }
                } else if (name.equals("content")) {
                    content = value;
                }
            }
            if (key != null && content != null && !meta.containsKey(key)) {
                meta.put(key, content);
            }
        }
        return meta;
    }

    private static String first(Map<String, String> meta, String... keys) {
        for (String key : keys) {
            String value = meta.get(key);
            if (value != null && value.trim().length() > 0) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static String unescape(String text) {
        if (text == null) {
            return "";
        }
        if (text.indexOf('&') < 0) {
            return text;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return Html.fromHtml(text).toString();
    }

    private static String clean(String text, int maxLength) {
        String value = unescape(text).replaceAll("\\s+", " ").trim();
        return value.length() > maxLength ? value.substring(0, maxLength - 1).trim() + "…" : value;
    }

    private static File downloadImage(Context context, String imageUrl, String pageUrl) {
        try {
            String address = imageUrl;
            if (address.toLowerCase(Locale.US).startsWith("http://")) {
                address = "https://" + address.substring(7);
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            byte[] bytes;
            try {
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Referer", pageUrl);
                if (connection.getResponseCode() != 200) {
                    return null;
                }
                bytes = readBytes(connection.getInputStream(), MAX_IMAGE_BYTES, false);
            } finally {
                connection.disconnect();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= IMAGE_MAX_SIDE_PX) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) {
                return null;
            }
            File file = new File(cacheDir(context), key(pageUrl) + ".jpg");
            FileOutputStream out = new FileOutputStream(file);
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } finally {
                out.close();
                bitmap.recycle();
            }
            return file;
        } catch (Throwable e) {
            return null;
        }
    }

    // ---- the temporary memory ----

    private static File cacheDir(Context context) {
        File dir = new File(context.getCacheDir(), CACHE_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static String key(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(url.getBytes("UTF-8"));
            StringBuilder text = new StringBuilder();
            for (byte b : hash) {
                text.append(String.format(Locale.US, "%02x", b));
            }
            return text.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    private static LinkPreview readCache(Context context, String url) {
        try {
            File file = new File(cacheDir(context), key(url) + ".json");
            if (!file.exists()) {
                return null;
            }
            byte[] bytes = readBytes(new java.io.FileInputStream(file), 64 * 1024, false);
            JSONObject json = new JSONObject(new String(bytes, "UTF-8"));
            LinkPreview preview = new LinkPreview();
            preview.url = url;
            preview.title = json.optString("title", "");
            preview.description = json.optString("description", "");
            preview.siteName = json.optString("siteName", "");
            File image = new File(cacheDir(context), key(url) + ".jpg");
            preview.imageFile = image.exists() ? image : null;
            return preview;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void writeCache(Context context, LinkPreview preview) {
        try {
            JSONObject json = new JSONObject();
            json.put("title", preview.title);
            json.put("description", preview.description);
            json.put("siteName", preview.siteName);
            FileOutputStream out = new FileOutputStream(new File(cacheDir(context), key(preview.url) + ".json"));
            try {
                out.write(json.toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
            //Not kept: it is read again next time
        }
    }
}
